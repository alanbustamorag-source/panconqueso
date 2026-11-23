/* Bodriosafio: native Windows effect, exact user supplied 1.26.60.5 build.
 * The module contains no Minecraft executable or replacement game assets.
 * DllMain does no patching. Initialize is invoked outside the loader lock.
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <stdint.h>
#include <string.h>
#include "profile.h"
#define COUNT(a) (sizeof(a)/sizeof((a)[0]))
#define EXPORT __declspec(dllexport)
#define REGISTRY_RVA 0x12970570U
#define ALLOCATOR_RVA 0x1233c1a8U
#define FACTORY_RVA 0x3558f40U
#define DELETE_RVA 0xe934b00U

static HMODULE own_module;
static unsigned char *game,*arena;
static void **registry;
static volatile LONG state,initializing,registering;
static DWORD last_error;
static void *own_effect;
static void (*original_init)(const void *,const void *);
static uintptr_t (*original_hud)(void *);
static HANDLE paused[1024];
static unsigned paused_count;

static void status(const char *phase) {
    wchar_t path[32768];
    DWORD n=GetModuleFileNameW(own_module,path,32768);
    if (!n || n>=32700) return;
    while (n && path[n-1]!=L'\\' && path[n-1]!=L'/') --n;
    lstrcpyW(path+n,L"BodriosafioEffects-status.json");
    char text[512];
    int length=wsprintfA(text,"{\"module\":\"bodriosafio-native-effects-windows\",\"version\":\"0.1.0\",\"phase\":\"%s\",\"effect_id\":39,\"identifier\":\"bodriosafio:anticombustion\",\"minecraft\":\"1.26.60.5\",\"error\":%lu}\r\n",phase,(unsigned long)last_error);
    HANDLE f=CreateFileW(path,GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE,NULL,CREATE_ALWAYS,FILE_ATTRIBUTE_NORMAL,NULL);
    if (f==INVALID_HANDLE_VALUE) return;
    DWORD written; WriteFile(f,text,(DWORD)length,&written,NULL); CloseHandle(f);
}

static void *allocate_game(size_t size) {
    void *allocator=*(void **)(game+ALLOCATOR_RVA);
    if (!allocator) return NULL;
    void **vtable=*(void ***)allocator;
    return ((void *(*)(void *,size_t))vtable[1])(allocator,size);
}

typedef struct {
    union { char local[16]; const char *pointer; } data;
    uint64_t size,capacity;
} NativeString;

static void register_effect(void) {
    static const char identifier[]="bodriosafio:anticombustion";
    if (!registry || own_effect || InterlockedCompareExchange(&registering,1,0)) return;
    if (registry[39]) { state=-1;last_error=ERROR_ALREADY_EXISTS;status("id_collision");goto done; }
    // These are read-only string arguments. The engine copies the name into
    // its own MSVC string, allocator and native hash/name map.
    NativeString name={0};
    name.data.pointer=identifier;name.size=sizeof(identifier)-1;name.capacity=name.size;
    unsigned char harmful=0;
    int color=0xff9166,icon=0;
    typedef void *(*Factory)(unsigned,const NativeString *,const char *,const unsigned char *,const int *,const int *);
    void *effect=((Factory)(game+FACTORY_RVA))(39,&name,"effect.bodriosafio.anticombustion",&harmful,&color,&icon);
    if (!effect || registry[39]!=effect || *(unsigned *)((unsigned char *)effect+8)!=39) {
        state=-1;last_error=ERROR_INVALID_DATA;status("registration_failed");goto done;
    }
    NativeString *stem=(NativeString *)((unsigned char *)effect+0xd0);
    if (stem->size!=0 || stem->capacity!=15) {
        state=-1;last_error=ERROR_INVALID_DATA;status("unexpected_icon_layout");goto done;
    }
    memcpy(stem->data.local,"anticombustion",15);
    stem->size=14;
    own_effect=effect;state=2;last_error=0;status("registered");
done:
    InterlockedExchange(&registering,0);
}

static void init_hook(const void *version,const void *experiments) {
    original_init(version,experiments);
    register_effect();
}

static int ensure_hud(void *self) {
    unsigned char **vector=(unsigned char **)((unsigned char *)self+0x18);
    size_t length=(size_t)(vector[1]-vector[0]);
    if (length>=40*36) return 1;
    if (length!=39*36) return 0;
    unsigned char *expanded=allocate_game(40*36);
    if (!expanded) return 0;
    memcpy(expanded,vector[0],length);memset(expanded+length,0,36);
    void *old=vector[0];
    vector[0]=expanded;vector[1]=vector[2]=expanded+40*36;
    ((void (*)(void *,size_t))(game+DELETE_RVA))(old,length);
    return 1;
}

static uintptr_t hud_hook(void *self) {
    // Late attachment is supported: resize a HUD that was already built at
    // the main menu before publishing the new effect to scripts.
    if (ensure_hud(self)) register_effect();
    return original_hud(self);
}

static int put(void *address,const void *bytes,size_t length) {
    DWORD previous,ignored;
    if (!VirtualProtect(address,length,PAGE_EXECUTE_READWRITE,&previous)) return 0;
    memcpy(address,bytes,length);
    int flushed=FlushInstructionCache(GetCurrentProcess(),address,length)!=0;
    int restored=VirtualProtect(address,length,previous,&ignored)!=0;
    return flushed && restored;
}

static int supported(void) {
    IMAGE_DOS_HEADER *dos=(IMAGE_DOS_HEADER *)game;
    if (dos->e_magic!=IMAGE_DOS_SIGNATURE || dos->e_lfanew<0 || dos->e_lfanew>0x1000) return 0;
    IMAGE_NT_HEADERS64 *nt=(IMAGE_NT_HEADERS64 *)(game+dos->e_lfanew);
    if (nt->Signature!=IMAGE_NT_SIGNATURE || nt->FileHeader.Machine!=IMAGE_FILE_MACHINE_AMD64
        || nt->FileHeader.TimeDateStamp!=GAME_TIMESTAMP || nt->OptionalHeader.SizeOfImage!=GAME_IMAGE_SIZE
        || memcmp(game+RSDS_RVA,expected_rsds,sizeof expected_rsds)) return 0;
    for (size_t i=0;i<COUNT(patches);++i)
        if (memcmp(game+patches[i].rva,patches[i].before,patches[i].size)) return 0;
    for (size_t i=0;i<COUNT(references);++i)
        if (memcmp(game+references[i].rva,references[i].before,references[i].size)) return 0;
    for (size_t i=0;i<COUNT(hook_profiles);++i)
        if (memcmp(game+hook_profiles[i].rva,hook_profiles[i].before,hook_profiles[i].size)) return 0;
    return 1;
}

static void jump(unsigned char *at,void *target) {
    at[0]=0xff;at[1]=0x25;memset(at+2,0,4);memcpy(at+6,&target,8);
}

static int sensitive(uintptr_t ip) {
    if (ip>=(uintptr_t)game+0x35541b0 && ip<(uintptr_t)game+0x3557745) return 1;
    for (size_t i=0;i<COUNT(patches);++i)
        if (ip>=(uintptr_t)game+patches[i].rva && ip<(uintptr_t)game+patches[i].rva+patches[i].size) return 1;
    for (size_t i=0;i<COUNT(references);++i)
        if (ip>=(uintptr_t)game+references[i].rva && ip<(uintptr_t)game+references[i].rva+references[i].size) return 1;
    for (size_t i=0;i<COUNT(hook_profiles);++i)
        if (ip>=(uintptr_t)game+hook_profiles[i].rva && ip<(uintptr_t)game+hook_profiles[i].rva+hook_profiles[i].size) return 1;
    return 0;
}

static void resume_all(void) {
    while (paused_count) { HANDLE h=paused[--paused_count];ResumeThread(h);CloseHandle(h); }
}

static int pause_others(void) {
    HANDLE opened[1024];unsigned count=0;
    HANDLE snapshot=CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD,0);
    if (snapshot==INVALID_HANDLE_VALUE) return 0;
    THREADENTRY32 entry={0};entry.dwSize=sizeof entry;
    DWORD pid=GetCurrentProcessId(),self=GetCurrentThreadId();
    int okay=1;
    if (Thread32First(snapshot,&entry)) do {
        if (entry.th32OwnerProcessID!=pid || entry.th32ThreadID==self) continue;
        if (count==COUNT(opened)) {okay=0;break;}
        HANDLE h=OpenThread(THREAD_SUSPEND_RESUME|THREAD_GET_CONTEXT|THREAD_QUERY_INFORMATION,FALSE,entry.th32ThreadID);
        if (!h) {okay=0;break;}
        opened[count++]=h;
    } while (Thread32Next(snapshot,&entry));
    CloseHandle(snapshot);
    // Open all handles before pausing a thread. No allocator or loader work
    // occurs while other game threads are suspended.
    for (unsigned i=0;okay && i<count;++i) {
        if (SuspendThread(opened[i])==(DWORD)-1) {okay=0;break;}
        paused[paused_count++]=opened[i];opened[i]=NULL;
        CONTEXT context={0};context.ContextFlags=CONTEXT_CONTROL;
        if (!GetThreadContext(paused[paused_count-1],&context) || sensitive(context.Rip)) okay=0;
    }
    for (unsigned i=0;i<count;++i) if (opened[i]) CloseHandle(opened[i]);
    if (!okay) resume_all();
    return okay;
}

static int rollback(void) {
    int okay=1;
    for (size_t i=0;i<COUNT(hook_profiles);++i)
        if (!put(game+hook_profiles[i].rva,hook_profiles[i].before,hook_profiles[i].size)) okay=0;
    for (size_t i=0;i<COUNT(references);++i)
        if (!put(game+references[i].rva,references[i].before,references[i].size)) okay=0;
    for (size_t i=0;i<COUNT(patches);++i)
        if (!put(game+patches[i].rva,patches[i].before,patches[i].size)) okay=0;
    return okay;
}

EXPORT DWORD WINAPI BodriosafioInitialize(void *unused) {
    (void)unused;
    if (state>0) return ERROR_SUCCESS;
    if (InterlockedCompareExchange(&initializing,1,0)) return ERROR_BUSY;
    DWORD result=ERROR_SUCCESS;
    game=(unsigned char *)GetModuleHandleW(NULL);
    if (!game || !supported()) {result=ERROR_REVISION_MISMATCH;goto done;}
    // The normal launcher attaches after Minecraft has initialized its map.
    // Refuse a partial initialization; the activator can retry after a frame.
    if (!((void **)(game+REGISTRY_RVA))[1] || !((void **)(game+REGISTRY_RVA))[38]) {
        result=ERROR_RETRY;goto done;
    }
    uintptr_t start=((uintptr_t)game+GAME_IMAGE_SIZE+0xffff)&~(uintptr_t)0xffff;
    for (unsigned i=0;i<4096 && !arena;++i)
        arena=VirtualAlloc((void *)(start+(uintptr_t)i*65536),0x4000,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);
    if (!arena) {result=ERROR_NOT_ENOUGH_MEMORY;goto done;}
    registry=(void **)arena;memset(arena,0,0x4000);
    for (size_t i=0;i<COUNT(hook_profiles);++i) {
        const struct HookProfile *h=hook_profiles+i;
        unsigned char *trampoline=arena+0x1000+i*0x100;
        memcpy(trampoline,h->before,h->size);jump(trampoline+h->size,game+h->rva+h->size);
        if (i==0) original_init=(void (*)(const void *,const void *))trampoline;
        else original_hud=(uintptr_t (*)(void *))trampoline;
    }
    DWORD ignored;
    if (!VirtualProtect(arena+0x1000,0x3000,PAGE_EXECUTE_READ,&ignored)) {result=GetLastError();goto free_arena;}
    FlushInstructionCache(GetCurrentProcess(),arena+0x1000,0x3000);
    if (!pause_others()) {result=ERROR_RETRY;goto free_arena;}
    if (!supported()) {result=ERROR_REVISION_MISMATCH;resume_all();goto free_arena;}
    memcpy(registry,game+REGISTRY_RVA,39*sizeof(void *));
    for (size_t i=0;i<COUNT(patches);++i)
        if (!put(game+patches[i].rva,patches[i].after,patches[i].size)) goto patch_failed;
    for (size_t i=0;i<COUNT(references);++i) {
        const struct TableRef *r=references+i;
        unsigned char bytes[24];memcpy(bytes,r->before,r->size);
        int64_t disp=(int64_t)((uintptr_t)registry+r->target-REGISTRY_RVA)-(int64_t)((uintptr_t)game+r->rva+r->size);
        if (disp<INT32_MIN || disp>INT32_MAX) goto patch_failed;
        int32_t encoded=(int32_t)disp;memcpy(bytes+r->disp,&encoded,4);
        if (!put(game+r->rva,bytes,r->size)) goto patch_failed;
    }
    for (size_t i=0;i<COUNT(hook_profiles);++i) {
        unsigned char bytes[24];memset(bytes,0x90,sizeof bytes);
        jump(bytes,i==0?(void *)&init_hook:(void *)&hud_hook);
        if (!put(game+hook_profiles[i].rva,bytes,hook_profiles[i].size)) goto patch_failed;
    }
    state=1;resume_all();last_error=0;status("waiting_for_world");goto done;
patch_failed:
    result=ERROR_WRITE_FAULT;
    { int restored=rollback();resume_all();if (!restored) {state=-1;goto done;} }
free_arena:
    if (arena) VirtualFree(arena,0,MEM_RELEASE);
    arena=NULL;registry=NULL;
done:
    last_error=result;
    if (result!=ERROR_SUCCESS) status(result==ERROR_REVISION_MISMATCH?"unsupported_build":result==ERROR_RETRY?"retry":"error");
    InterlockedExchange(&initializing,0);
    return result;
}

EXPORT DWORD WINAPI BodriosafioEffectState(void *unused) { (void)unused;return (DWORD)state; }

BOOL WINAPI DllMain(HINSTANCE module,DWORD reason,LPVOID reserved) {
    (void)reserved;
    if (reason==DLL_PROCESS_ATTACH) {own_module=module;DisableThreadLibraryCalls(module);}
    return TRUE;
}
