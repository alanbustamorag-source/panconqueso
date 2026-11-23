#include "backend.hpp"
#include "profile.hpp"
#include "update_channel.hpp"
#include "native_exports.hpp"
#include "launcher_update.hpp"
#include "effects_readiness.hpp"
#include <winhttp.h>
#include <tlhelp32.h>
#include <shlobj.h>
#include <shellapi.h>
#include <appmodel.h>
#include <aclapi.h>
#include <bcrypt.h>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <vector>
#include <algorithm>
#include <mutex>
#include <stdexcept>

namespace fs = std::filesystem;
namespace bodrio {
namespace {
struct Handle {
    HANDLE h = nullptr;
    explicit Handle(HANDLE value = nullptr) : h(value) {}
    ~Handle() { if (h && h != INVALID_HANDLE_VALUE) CloseHandle(h); }
    Handle(const Handle&) = delete;
    Handle& operator=(const Handle&) = delete;
    explicit operator bool() const { return h && h != INVALID_HANDLE_VALUE; }
};
std::mutex log_mutex;

std::string utf8(const std::wstring& s) {
    if (s.empty()) return {};
    int n = WideCharToMultiByte(CP_UTF8,0,s.data(),static_cast<int>(s.size()),nullptr,0,nullptr,nullptr);
    if (!n) throw std::runtime_error("No se pudo convertir el texto a UTF-8.");
    std::string out(n,'\0');
    WideCharToMultiByte(CP_UTF8,0,s.data(),static_cast<int>(s.size()),out.data(),n,nullptr,nullptr);
    return out;
}
std::wstring wide(const std::string& s) {
    if (s.empty()) return {};
    int n = MultiByteToWideChar(CP_UTF8,0,s.data(),static_cast<int>(s.size()),nullptr,0);
    if (!n) return L"Error sin descripción.";
    std::wstring out(n,L'\0');
    MultiByteToWideChar(CP_UTF8,0,s.data(),static_cast<int>(s.size()),out.data(),n);
    return out;
}
[[noreturn]] void fail(const std::wstring& text, DWORD code = GetLastError()) {
    wchar_t* message = nullptr;
    FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER|FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
                   nullptr,code,0,reinterpret_cast<LPWSTR>(&message),0,nullptr);
    std::wstring full = text + L" (Windows " + std::to_wstring(code) + L")";
    if (message) { full += L"\n"; full += message; LocalFree(message); }
    throw std::runtime_error(utf8(full));
}
std::string read_bytes(const fs::path& path, uintmax_t limit = 2 * 1024 * 1024) {
    auto size = fs::file_size(path);
    if (size > limit) throw std::runtime_error("El archivo excede el tamaño previsto.");
    std::ifstream f(path,std::ios::binary);
    if (!f) throw std::runtime_error("No se pudo leer el archivo.");
    std::string text(static_cast<size_t>(size),'\0');
    if (!text.empty() && !f.read(text.data(),static_cast<std::streamsize>(text.size())))
        throw std::runtime_error("No se pudo leer el archivo completo.");
    return text;
}
std::string sha256(const std::string& bytes) {
    BCRYPT_ALG_HANDLE alg = nullptr;
    BCRYPT_HASH_HANDLE hash = nullptr;
    if (BCryptOpenAlgorithmProvider(&alg,BCRYPT_SHA256_ALGORITHM,nullptr,0) < 0)
        throw std::runtime_error("No se pudo iniciar la verificación SHA-256.");
    NTSTATUS status = BCryptCreateHash(alg,&hash,nullptr,0,nullptr,0,0);
    UCHAR digest[32]{};
    if (status >= 0) status = BCryptHashData(hash,reinterpret_cast<PUCHAR>(const_cast<char*>(bytes.data())),static_cast<ULONG>(bytes.size()),0);
    if (status >= 0) status = BCryptFinishHash(hash,digest,sizeof(digest),0);
    if (hash) BCryptDestroyHash(hash);
    BCryptCloseAlgorithmProvider(alg,0);
    if (status < 0) throw std::runtime_error("No se pudo verificar el archivo.");
    std::ostringstream s;
    for (unsigned char c : digest) s << std::hex << std::setfill('0') << std::setw(2) << static_cast<unsigned>(c);
    return s.str();
}
void atomic_write(const fs::path& path, const std::string& bytes) {
    fs::create_directories(path.parent_path());
    fs::path temporary = path;
    temporary += L".tmp." + std::to_wstring(GetCurrentProcessId()) + L"." + std::to_wstring(GetTickCount64());
    {
        Handle h(CreateFileW(temporary.c_str(),GENERIC_WRITE,0,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,nullptr));
        if (!h) fail(L"No se pudo crear el archivo de configuración");
        DWORD done = 0;
        if (!WriteFile(h.h,bytes.data(),static_cast<DWORD>(bytes.size()),&done,nullptr) || done != bytes.size() || !FlushFileBuffers(h.h)) {
            auto code = GetLastError();
            // The temporary file is removed after its handle has closed below.
            CloseHandle(h.h); h.h=nullptr;
            DeleteFileW(temporary.c_str());
            fail(L"No se pudo guardar el archivo completo",code);
        }
    }
    if (!MoveFileExW(temporary.c_str(),path.c_str(),MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH)) {
        auto code=GetLastError(); DeleteFileW(temporary.c_str());
        fail(L"No se pudo aplicar el archivo; el original se conservó",code);
    }
}
void allow_packaged_read(const fs::path& path, bool directory) {
    BYTE sid[SECURITY_MAX_SID_SIZE]{}; DWORD sid_size=sizeof(sid);
    if (!CreateWellKnownSid(WinBuiltinAnyPackageSid,nullptr,sid,&sid_size)) fail(L"No se pudieron preparar los permisos de Minecraft");
    PACL old_acl=nullptr,new_acl=nullptr;
    PSECURITY_DESCRIPTOR descriptor=nullptr;
    DWORD status=GetNamedSecurityInfoW(const_cast<LPWSTR>(path.c_str()),SE_FILE_OBJECT,DACL_SECURITY_INFORMATION,
                                      nullptr,nullptr,&old_acl,nullptr,&descriptor);
    if (status != ERROR_SUCCESS) fail(L"No se pudieron leer los permisos del complemento",status);
    // A null DACL already permits access; do not narrow it accidentally.
    if (!old_acl) { LocalFree(descriptor); return; }
    EXPLICIT_ACCESSW access{};
    access.grfAccessPermissions=GENERIC_READ|GENERIC_EXECUTE;
    access.grfAccessMode=GRANT_ACCESS;
    access.grfInheritance=directory ? SUB_CONTAINERS_AND_OBJECTS_INHERIT : NO_INHERITANCE;
    access.Trustee.TrusteeForm=TRUSTEE_IS_SID;
    access.Trustee.TrusteeType=TRUSTEE_IS_WELL_KNOWN_GROUP;
    access.Trustee.ptstrName=reinterpret_cast<LPWSTR>(sid);
    status=SetEntriesInAclW(1,&access,old_acl,&new_acl);
    if (status==ERROR_SUCCESS)
        status=SetNamedSecurityInfoW(const_cast<LPWSTR>(path.c_str()),SE_FILE_OBJECT,DACL_SECURITY_INFORMATION,
                                     nullptr,nullptr,new_acl,nullptr);
    if (new_acl) LocalFree(new_acl);
    if (descriptor) LocalFree(descriptor);
    if (status != ERROR_SUCCESS) fail(L"Minecraft no podrá leer el complemento",status);
}
std::string resource(unsigned id) {
    auto instance=GetModuleHandleW(nullptr);
    HRSRC item=FindResourceW(instance,MAKEINTRESOURCEW(id),RT_RCDATA);
    if (!item) fail(L"Falta un archivo incluido en el launcher");
    auto size=SizeofResource(instance,item);
    HGLOBAL data=LoadResource(instance,item);
    auto ptr=static_cast<const char*>(LockResource(data));
    if (!ptr || !size) fail(L"No se pudo leer un archivo del launcher");
    return std::string(ptr,size);
}
fs::path extract(unsigned id, const std::wstring& filename, const char* expected) {
    auto bytes=resource(id);
    if (expected && sha256(bytes)!=expected) throw std::runtime_error("El archivo incluido no coincide con su SHA-256. No se cargó.");
    auto root=data_root();
    auto dir=root/L"runtime"/L"0.1.5";
    fs::create_directories(dir);
    // Limit ACL additions to our own new folders, never the whole AppData tree.
    allow_packaged_read(root.parent_path(),true);
    allow_packaged_read(root,true);
    allow_packaged_read(root/L"runtime",true);
    allow_packaged_read(dir,true);
    auto path=dir/filename;
    if (!fs::exists(path) || read_bytes(path)!=bytes) atomic_write(path,bytes);
    allow_packaged_read(path,false);
    return path;
}
bool same_owner(HANDLE process) {
    Handle a,b;
    if (!OpenProcessToken(process,TOKEN_QUERY,&a.h) || !OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&b.h)) return false;
    DWORD an=0,bn=0;
    GetTokenInformation(a.h,TokenUser,nullptr,0,&an); GetTokenInformation(b.h,TokenUser,nullptr,0,&bn);
    if (!an || !bn) return false;
    std::vector<BYTE> av(an),bv(bn);
    if (!GetTokenInformation(a.h,TokenUser,av.data(),an,&an) || !GetTokenInformation(b.h,TokenUser,bv.data(),bn,&bn)) return false;
    return EqualSid(reinterpret_cast<TOKEN_USER*>(av.data())->User.Sid,reinterpret_cast<TOKEN_USER*>(bv.data())->User.Sid)!=0;
}
std::wstring image_path(HANDLE process) {
    std::wstring image(32768,L'\0'); DWORD count=static_cast<DWORD>(image.size());
    if (!QueryFullProcessImageNameW(process,0,image.data(),&count)) fail(L"No se pudo comprobar la instalación de Minecraft");
    image.resize(count);
    if (_wcsicmp(fs::path(image).filename().c_str(),L"Minecraft.Windows.exe")!=0)
        throw std::runtime_error("El proceso seleccionado no es Minecraft Bedrock.");
    return image;
}
DWORD find_game() {
    Handle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS,0));
    if (!snapshot) fail(L"No se pudo buscar Minecraft");
    PROCESSENTRY32W entry{}; entry.dwSize=sizeof(entry);
    DWORD current_session=0; ProcessIdToSessionId(GetCurrentProcessId(),&current_session);
    DWORD pid=0;
    if (!Process32FirstW(snapshot.h,&entry)) return 0;
    do {
        if (_wcsicmp(entry.szExeFile,L"Minecraft.Windows.exe")!=0) continue;
        DWORD session=0;
        if (!ProcessIdToSessionId(entry.th32ProcessID,&session) || session!=current_session) continue;
        Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION,FALSE,entry.th32ProcessID));
        if (!process) fail(L"Minecraft está abierto, pero Windows no permitió comprobarlo. Usa el mismo nivel de permisos para ambos");
        if (!same_owner(process.h)) continue;
        if (pid) throw std::runtime_error("Hay más de un Minecraft abierto. Deja solo el que vas a usar y pulsa Jugar.");
        pid=entry.th32ProcessID;
    } while (Process32NextW(snapshot.h,&entry));
    return pid;
}
struct GameVersion {
    unsigned a,b,c,d;
    std::wstring raw,display;
};
GameVersion read_game_version(const fs::path& image) {
    DWORD size=GetFileVersionInfoSizeW(image.c_str(),nullptr);
    if (!size) fail(L"No se pudo leer la versión de Minecraft; no se cargó el complemento");
    std::vector<BYTE> data(size);
    if (!GetFileVersionInfoW(image.c_str(),0,size,data.data())) fail(L"No se pudo leer la versión de Minecraft");
    VS_FIXEDFILEINFO* info=nullptr; UINT count=0;
    if (!VerQueryValueW(data.data(),L"\\",reinterpret_cast<LPVOID*>(&info),&count) || count<sizeof(*info) || info->dwSignature!=0xFEEF04BD)
        throw std::runtime_error("La información de versión no es válida; no se cargó el complemento.");
    unsigned a=HIWORD(info->dwFileVersionMS), b=LOWORD(info->dwFileVersionMS);
    unsigned c=HIWORD(info->dwFileVersionLS), d=LOWORD(info->dwFileVersionLS);
    std::wstring version=std::to_wstring(a)+L"."+std::to_wstring(b)+L"."+std::to_wstring(c)+L"."+std::to_wstring(d);
    return {a,b,c,d,version,wide(game_display_version(a,b,c,d))};
}
GameVersion verify_game_version(const fs::path& image, const Reporter& report) {
    auto version=read_game_version(image);
    // Publish the actual file version even when it is incompatible with this profile.
    report({L"Comprobando compatibilidad…",1,version.display});
    if (!supported_version(version.a,version.b,version.c,version.d))
        throw std::runtime_error(utf8(L"Detecté Minecraft "+version.display+L". Versiones objetivo: 26.60, 26.62 y 26.63; no se cambió su configuración."));
    std::ifstream f(image,std::ios::binary);
    IMAGE_DOS_HEADER dos{}; f.read(reinterpret_cast<char*>(&dos),sizeof(dos));
    if (!f || dos.e_magic!=IMAGE_DOS_SIGNATURE || dos.e_lfanew<=0 || dos.e_lfanew>1024*1024)
        throw std::runtime_error("El ejecutable de Minecraft no es válido.");
    f.seekg(dos.e_lfanew);
    DWORD signature=0; IMAGE_FILE_HEADER header{};
    f.read(reinterpret_cast<char*>(&signature),sizeof(signature));
    f.read(reinterpret_cast<char*>(&header),sizeof(header));
    if (!f || signature!=IMAGE_NT_SIGNATURE || header.Machine!=IMAGE_FILE_MACHINE_AMD64)
        throw std::runtime_error("Este launcher necesita Minecraft para Windows de 64 bits (x64).");
    log_line(L"Versión del ejecutable: "+version.raw);
    return version;
}
fs::path config_for(HANDLE process, const fs::path& image) {
    wchar_t family[1024]{}; UINT32 length=1024;
    LONG rc=GetPackageFamilyName(process,&length,family);
    fs::path base;
    if (rc==ERROR_SUCCESS) {
        std::wstring fam(family),lower=fam;
        std::transform(lower.begin(),lower.end(),lower.begin(),::towlower);
        if (lower.find(L"microsoft.minecraft")!=0 || lower.find(L"_8wekyb3d8bbwe")==std::wstring::npos || lower.find(L"beta")!=std::wstring::npos)
            throw std::runtime_error("Esta primera versión está preparada para Minecraft Bedrock normal. No se modificó la instalación encontrada.");
        auto local=data_root().parent_path().parent_path();
        base=local/L"Packages"/fam/L"RoamingState";
    } else if (rc==APPMODEL_ERROR_NO_PACKAGE) {
        // Exactly matches BuildLimitChanger's documented non-packaged fallback.
        base=image.parent_path();
    } else fail(L"No se pudo localizar la carpeta que usa Minecraft",static_cast<DWORD>(rc));
    return base/L"BuildLimitChanger"/L"config.ini";
}
std::vector<MODULEENTRY32W> modules(DWORD pid) {
    HANDLE raw=INVALID_HANDLE_VALUE;
    for (unsigned i=0;i<5;++i) {
        raw=CreateToolhelp32Snapshot(TH32CS_SNAPMODULE|TH32CS_SNAPMODULE32,pid);
        if (raw!=INVALID_HANDLE_VALUE || GetLastError()!=ERROR_BAD_LENGTH) break;
    }
    Handle snapshot(raw);
    if (!snapshot) fail(L"No se pudieron comprobar los complementos de Minecraft");
    MODULEENTRY32W entry{}; entry.dwSize=sizeof(entry);
    std::vector<MODULEENTRY32W> out;
    if (!Module32FirstW(snapshot.h,&entry)) fail(L"Minecraft todavía no está listo para cargar el complemento");
    do { out.push_back(entry); } while (Module32NextW(snapshot.h,&entry));
    return out;
}
bool existing_native(DWORD pid) {
    for (const auto& module : modules(pid)) {
        std::wstring name=module.szModule;
        std::transform(name.begin(),name.end(),name.begin(),::towlower);
        if (name.find(L"buildlimitchanger")==std::wstring::npos || fs::path(name).extension()!=L".dll") continue;
        if (sha256(read_bytes(module.szExePath))!=native_sha256)
            throw std::runtime_error("Hay otra versión de BuildLimitChanger cargada. Cierra Minecraft y vuelve a abrirlo con este launcher.");
        log_line(L"La misma biblioteca ya está cargada; no se repetirá la carga.");
        return true;
    }
    return false;
}
uintptr_t library_base(DWORD pid,const std::wstring& name,const char* expected) {
    for(const auto& module:modules(pid)) {
        if(_wcsicmp(module.szModule,name.c_str())!=0) continue;
        if(sha256(read_bytes(module.szExePath))!=expected)
            throw std::runtime_error("Minecraft tiene otra versión de "+utf8(name)+" cargada. Cierra Minecraft y pulsa Jugar de nuevo.");
        return reinterpret_cast<uintptr_t>(module.modBaseAddr);
    }
    return 0;
}
bool write_profile(const fs::path& config) {
    std::string old=fs::exists(config) ? read_bytes(config) : std::string{};
    std::string desired=configure_nether(old);
    if (desired==old) { log_line(L"La configuración ya coincide con el perfil."); return false; }
    fs::create_directories(config.parent_path());
    if (fs::exists(config)) {
        SYSTEMTIME t{}; GetLocalTime(&t);
        wchar_t suffix[128]{};
        swprintf(suffix,128,L".antes_launcher.%04u%02u%02u_%02u%02u%02u_%lu_%llu.bak",
                 t.wYear,t.wMonth,t.wDay,t.wHour,t.wMinute,t.wSecond,GetCurrentProcessId(),GetTickCount64());
        fs::path backup=config; backup+=suffix;
        if (!CopyFileW(config.c_str(),backup.c_str(),TRUE)) fail(L"No se pudo guardar una copia de la configuración anterior; no se modificó");
        log_line(L"Copia anterior: "+backup.wstring());
    }
    atomic_write(config,desired);
    log_line(L"Perfil Nether min=0 max=256 preparado en "+config.wstring());
    return true;
}
void inject_library(DWORD pid, const fs::path& dll,const char* expected,const std::wstring& name) {
    if (sha256(read_bytes(dll))!=expected) throw std::runtime_error("La biblioteca cambió después de verificarla; no se cargó.");
    Handle process(OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_CREATE_THREAD|PROCESS_VM_OPERATION|PROCESS_VM_WRITE|PROCESS_VM_READ,FALSE,pid));
    if (!process) fail(L"Windows no permitió cargar el complemento. Abre Minecraft y el launcher con el mismo nivel de permisos");
    auto local_proc=GetProcAddress(GetModuleHandleW(L"kernel32.dll"),"LoadLibraryW");
    HMODULE owner=nullptr;
    if (!local_proc || !GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS|GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                                          reinterpret_cast<LPCWSTR>(local_proc),&owner))
        fail(L"No se pudo preparar la carga del complemento");
    wchar_t owner_path[32768]{};
    if (!GetModuleFileNameW(owner,owner_path,32768)) fail(L"No se pudo comprobar la biblioteca de Windows");
    auto wanted=fs::path(owner_path).filename().wstring();
    uintptr_t remote_base=0;
    for (const auto& module : modules(pid))
        if (_wcsicmp(module.szModule,wanted.c_str())==0) remote_base=reinterpret_cast<uintptr_t>(module.modBaseAddr);
    if (!remote_base) throw std::runtime_error("Minecraft todavía no tiene disponibles sus bibliotecas de Windows. Vuelve a intentarlo desde el menú principal.");
    auto offset=reinterpret_cast<uintptr_t>(local_proc)-reinterpret_cast<uintptr_t>(owner);
    auto start=reinterpret_cast<LPTHREAD_START_ROUTINE>(remote_base+offset);
    auto path=dll.wstring(); SIZE_T length=(path.size()+1)*sizeof(wchar_t);
    LPVOID argument=VirtualAllocEx(process.h,nullptr,length,MEM_RESERVE|MEM_COMMIT,PAGE_READWRITE);
    if (!argument) fail(L"No se pudo preparar la memoria para el complemento");
    SIZE_T written=0;
    if (!WriteProcessMemory(process.h,argument,path.c_str(),length,&written) || written!=length) {
        DWORD code=GetLastError(); VirtualFreeEx(process.h,argument,0,MEM_RELEASE);
        fail(L"No se pudo enviar la ruta del complemento",code);
    }
    Handle thread(CreateRemoteThread(process.h,nullptr,0,start,argument,0,nullptr));
    if (!thread) {
        DWORD code=GetLastError(); VirtualFreeEx(process.h,argument,0,MEM_RELEASE);
        fail(L"Windows no permitió iniciar la carga del complemento",code);
    }
    DWORD wait=WaitForSingleObject(thread.h,20000);
    if (wait!=WAIT_OBJECT_0) {
        // Do NOT free a path that a pending remote thread may still be reading.
        // Do NOT terminate it or automatically inject another copy.
        throw LoadUncertain("Windows no confirmó el final de la carga. Cierra Minecraft y el launcher; después vuelve a abrir el launcher. El botón Jugar se bloqueó para evitar repetir una carga sin confirmar.");
    }
    VirtualFreeEx(process.h,argument,0,MEM_RELEASE);
    // A 64-bit HMODULE cannot be verified with the truncated DWORD thread exit code.
    if (!library_base(pid,name,expected)) throw std::runtime_error("Minecraft no cargó "+utf8(name)+". Revisa el registro del launcher.");
    log_line(name+L" cargada y verificada en la lista de módulos.");
}
void prepare_effects(DWORD pid,const fs::path& dll,const Reporter& report,const std::function<bool()>& cancelled) {
    auto bytes=read_bytes(dll);
    if(sha256(bytes)!=effects_sha256) throw std::runtime_error("La DLL de Anticombustión no coincide con el archivo incluido.");
    auto init=native_export_rva(bytes,"BodriosafioInitialize");
    auto base=library_base(pid,L"BodriosafioEffects.dll",effects_sha256);
    if(!base) {
        report({L"Cargando Anticombustión…",3});
        inject_library(pid,dll,effects_sha256,L"BodriosafioEffects.dll");
        base=library_base(pid,L"BodriosafioEffects.dll",effects_sha256);
    }
    Handle process(OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_CREATE_THREAD|PROCESS_VM_OPERATION|PROCESS_VM_WRITE|PROCESS_VM_READ,FALSE,pid));
    if(!process) fail(L"No se pudo inicializar Anticombustión");
    wait_for_effects([&] {
        Handle thread(CreateRemoteThread(process.h,nullptr,0,reinterpret_cast<LPTHREAD_START_ROUTINE>(base+init),nullptr,0,nullptr));
        if(!thread) fail(L"Windows no permitió preparar Anticombustión");
        if(WaitForSingleObject(thread.h,20000)!=WAIT_OBJECT_0)
            throw LoadUncertain("Anticombustión sigue inicializándose. Cierra Minecraft y el launcher antes de volver a intentarlo.");
        DWORD result=0;
        if(!GetExitCodeThread(thread.h,&result)) fail(L"No se pudo comprobar Anticombustión");
        return result;
    },[]{return GetTickCount64();},[](unsigned ms){Sleep(ms);},cancelled,[&]{report({L"Esperando a que Minecraft termine de iniciar…",3});});
    log_line(L"Anticombustión preparada automáticamente; el efecto se registrará al entrar al mundo.");
}
void open_document(unsigned id, const wchar_t* filename) {
    auto path=extract(id,filename,nullptr);
    auto result=reinterpret_cast<INT_PTR>(ShellExecuteW(nullptr,L"open",path.c_str(),nullptr,nullptr,SW_SHOWNORMAL));
    if (result<=32) fail(L"No se pudo abrir el archivo",static_cast<DWORD>(result));
}
}

fs::path data_root() {
    PWSTR local=nullptr;
    if (FAILED(SHGetKnownFolderPath(FOLDERID_LocalAppData,KF_FLAG_CREATE,nullptr,&local)))
        throw std::runtime_error("No se encontró la carpeta de datos del usuario de Windows.");
    fs::path result=fs::path(local)/L"Bodriosafio"/L"Launcher";
    CoTaskMemFree(local);
    return result;
}
fs::path log_path() { return data_root()/L"launcher.log"; }
std::wstring detect_game_version() {
    DWORD pid=find_game();
    if (pid) {
        Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION,FALSE,pid));
        if (!process) fail(L"No se pudo leer la versión del juego abierto");
        return read_game_version(image_path(process.h)).display;
    }
    // Read only the current user's registered Minecraft packages; do not launch or modify anything.
    const wchar_t* families[]={L"Microsoft.MinecraftUWP_8wekyb3d8bbwe",L"Microsoft.MinecraftWindows_8wekyb3d8bbwe"};
    for (auto family : families) {
        UINT32 count=0,characters=0;
        LONG rc=GetPackagesByPackageFamily(family,&count,nullptr,&characters,nullptr);
        if (rc!=ERROR_INSUFFICIENT_BUFFER || !count || count>256 || !characters || characters>1024*1024) continue;
        std::vector<PWSTR> names(count);
        std::vector<wchar_t> buffer(characters);
        if (GetPackagesByPackageFamily(family,&count,names.data(),&characters,buffer.data())!=ERROR_SUCCESS) continue;
        for (UINT32 i=0;i<count;++i) {
            UINT32 size=0;
            if (GetPackagePathByFullName(names[i],&size,nullptr)!=ERROR_INSUFFICIENT_BUFFER || !size || size>32768) continue;
            std::vector<wchar_t> location(size);
            if (GetPackagePathByFullName(names[i],&size,location.data())!=ERROR_SUCCESS) continue;
            fs::path directory(location.data());
            for (const auto& candidate : {directory/L"Minecraft.Windows.exe",directory/L"Content"/L"Minecraft.Windows.exe"}) {
                std::error_code error;
                if (!fs::is_regular_file(candidate,error)) continue;
                try { return read_game_version(candidate).display; } catch (const std::exception&) { /* Try the next registered candidate. */ }
            }
        }
    }
    return {};
}
void log_line(const std::wstring& text) {
    std::lock_guard<std::mutex> lock(log_mutex);
    try {
        auto path=log_path(); fs::create_directories(path.parent_path());
        SYSTEMTIME t{}; GetLocalTime(&t);
        std::ofstream file(path,std::ios::binary|std::ios::app);
        file << std::setfill('0') << std::setw(4) << t.wYear << '-' << std::setw(2) << t.wMonth << '-' << std::setw(2) << t.wDay
             << ' ' << std::setw(2) << t.wHour << ':' << std::setw(2) << t.wMinute << ':' << std::setw(2) << t.wSecond
             << " | " << utf8(text) << "\r\n";
    } catch (...) { /* A log failure must not conceal the original user-facing error. */ }
}
LaunchResult prepare_running_game(DWORD pid, const Reporter& report,const std::function<bool()>& cancelled) {
    Handle query(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION|SYNCHRONIZE,FALSE,pid));
    if (!query) fail(L"Minecraft se cerró antes de preparar el perfil");
    if (!same_owner(query.h)) throw std::runtime_error("Minecraft pertenece a otra sesión de usuario.");
    auto image=fs::path(image_path(query.h));
    report({L"Leyendo la versión de Minecraft…",1});
    auto version=verify_game_version(image,report);
    if(!supported_effects_version(version.a,version.b,version.c,version.d)) {
        report({L"Minecraft abierto en modo addon; los módulos nativos requieren validación para esta compilación.",4,version.display});
        log_line(L"Modo addon: no se carga ninguna DLL ni se modifica el perfil de dimensiones en esta compilación.");
        return {version.raw,{},false,false,version.display,false};
    }
    auto config=config_for(query.h,image);
    bool loaded=existing_native(pid);
    report({L"Preparando la configuración…",2});
    auto dll=extract(201,L"windows-x64-BuildLimitChanger.dll",native_sha256);
    bool changed=write_profile(config);
    if (!loaded) {
        report({L"Cargando el complemento en Minecraft…",3});
        inject_library(pid,dll,native_sha256,L"windows-x64-BuildLimitChanger.dll");
    }
    auto effects=extract(205,L"BodriosafioEffects.dll",effects_sha256);
    prepare_effects(pid,effects,report,cancelled);
    return {version.raw,config,loaded,changed,version.display,true};
}
LaunchResult launch_and_prepare(const Reporter& report, const std::function<bool()>& cancelled) {
    log_line(L"Bodriosafio Launcher 0.1.5 | Perfil Minecraft 26.60.5 | Nether y Anticombustión | Inicio solicitado por el usuario.");
    report({L"Buscando Minecraft…",0});
    DWORD pid=find_game();
    if (!pid) {
        if (cancelled()) throw std::runtime_error("Preparación cancelada.");
        report({L"Abriendo Minecraft…",0});
        auto result=reinterpret_cast<INT_PTR>(ShellExecuteW(nullptr,L"open",L"minecraft://",nullptr,nullptr,SW_SHOWNORMAL));
        if (result<=32) throw std::runtime_error("Windows no pudo abrir Minecraft. Abre tu Minecraft Bedrock instalado y pulsa Jugar otra vez.");
        auto deadline=GetTickCount64()+90000;
        while (GetTickCount64()<deadline && !pid) {
            if (cancelled()) throw std::runtime_error("Preparación cancelada.");
            Sleep(500); pid=find_game();
        }
        if (!pid) throw std::runtime_error("Minecraft no apareció en 90 segundos. Ábrelo hasta el menú principal y pulsa Jugar otra vez.");
    }
    Handle process(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION|SYNCHRONIZE,FALSE,pid));
    if (!process) fail(L"Minecraft se cerró durante el inicio");
    report({L"Esperando a que Minecraft esté disponible…",0});
    for (unsigned i=0;i<60;++i) {
        if (cancelled()) throw std::runtime_error("Preparación cancelada.");
        if (WaitForSingleObject(process.h,0)==WAIT_OBJECT_0) throw std::runtime_error("Minecraft se cerró durante el inicio.");
        DWORD idle=WaitForInputIdle(process.h,500);
        if (idle==0 || idle==WAIT_FAILED) break; // UWP may not expose a conventional input queue.
    }
    if (cancelled()) throw std::runtime_error("Preparación cancelada.");
    return prepare_running_game(pid,report,cancelled);
}
namespace {
struct InternetHandle {
    HINTERNET h;
    explicit InternetHandle(HINTERNET value) : h(value) {}
    ~InternetHandle() { if(h) WinHttpCloseHandle(h); }
    InternetHandle(const InternetHandle&)=delete;
    InternetHandle& operator=(const InternetHandle&)=delete;
};
std::string download_channel_file(const std::string& url, size_t limit, const std::function<bool()>& cancelled) {
    validate_update_url(url);
    std::wstring address=wide(url);
    URL_COMPONENTS parts{};parts.dwStructSize=sizeof(parts);parts.dwHostNameLength=static_cast<DWORD>(-1);parts.dwUrlPathLength=static_cast<DWORD>(-1);
    if(!WinHttpCrackUrl(address.c_str(),static_cast<DWORD>(address.size()),0,&parts) || parts.nScheme!=INTERNET_SCHEME_HTTPS)
        throw std::runtime_error("La URL del canal no es válida.");
    std::wstring host(parts.lpszHostName,parts.dwHostNameLength),path(parts.lpszUrlPath,parts.dwUrlPathLength);
    InternetHandle session(WinHttpOpen(L"Bodriosafio-Launcher/0.1.5",WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,WINHTTP_NO_PROXY_NAME,WINHTTP_NO_PROXY_BYPASS,0));
    if(!session.h) fail(L"No se pudo iniciar la descarga");
    WinHttpSetTimeouts(session.h,5000,5000,10000,10000);
    InternetHandle connection(WinHttpConnect(session.h,host.c_str(),parts.nPort,0));
    if(!connection.h) fail(L"No se pudo conectar con el canal de actualizaciones");
    InternetHandle request(WinHttpOpenRequest(connection.h,L"GET",path.c_str(),nullptr,WINHTTP_NO_REFERER,WINHTTP_DEFAULT_ACCEPT_TYPES,WINHTTP_FLAG_SECURE));
    if(!request.h) fail(L"No se pudo preparar la descarga");
    DWORD redirects=WINHTTP_OPTION_REDIRECT_POLICY_NEVER;
    if(!WinHttpSetOption(request.h,WINHTTP_OPTION_REDIRECT_POLICY,&redirects,sizeof(redirects))) fail(L"No se pudo configurar la descarga");
    if(cancelled()) throw std::runtime_error("Actualización cancelada.");
    if(!WinHttpSendRequest(request.h,L"Cache-Control: no-cache\r\n",static_cast<DWORD>(-1),WINHTTP_NO_REQUEST_DATA,0,0,0)
        || !WinHttpReceiveResponse(request.h,nullptr)) fail(L"No se pudo descargar la actualización. Revisa tu conexión a Internet");
    DWORD status=0,bytes=sizeof(status);
    if(!WinHttpQueryHeaders(request.h,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&status,&bytes,WINHTTP_NO_HEADER_INDEX)) fail(L"No se pudo comprobar la respuesta del canal");
    if(status!=200) throw std::runtime_error("El canal de actualizaciones devolvió HTTP "+std::to_string(status)+".");
    std::string result; ULONGLONG started=GetTickCount64();
    while(true) {
        if(cancelled()) throw std::runtime_error("Actualización cancelada.");
        if(GetTickCount64()-started>120000) throw std::runtime_error("La descarga tardó demasiado. Vuelve a intentarlo.");
        DWORD available=0;
        if(!WinHttpQueryDataAvailable(request.h,&available)) fail(L"Se interrumpió la descarga");
        if(!available) break;
        if(result.size()+available>limit) throw std::runtime_error("La descarga supera el tamaño permitido.");
        std::vector<char> buffer(std::min<DWORD>(available,65536));DWORD received=0;
        if(!WinHttpReadData(request.h,buffer.data(),static_cast<DWORD>(buffer.size()),&received)) fail(L"No se pudo completar la descarga");
        if(!received) break;
        result.append(buffer.data(),received);
    }
    return result;
}
}
std::wstring available_addon_version() {
    auto remote=parse_update_channel(download_channel_file(update_url,128*1024,[]{return false;}));
    return wide(use_included_addon(remote.version)?included_addon_version:remote.version);
}
std::wstring update_addon(const Reporter& report, const std::function<bool()>& cancelled) {
    report({L"Buscando la última versión del addon…",0,{}});
    auto channel=parse_update_channel(download_channel_file(update_url,128*1024,cancelled));
    if(use_included_addon(channel.version)) {
        report({L"Preparando el addon incluido 0.3.15…",2,{}});
        if(cancelled()) throw std::runtime_error("Actualización cancelada.");
        import_addon();
        return L"Se abrió el addon incluido 0.3.15; el canal todavía ofrece una versión anterior. Espera a que Minecraft termine de importar.";
    }
    auto path=data_root()/L"updates"/wide(channel.version)/L"Bodriosafio.mcaddon";
    bool cached=false;
    if(fs::is_regular_file(path) && fs::file_size(path)==channel.size) {
        try {cached=sha256(read_bytes(path,64*1024*1024))==channel.sha256;} catch(const std::exception&) {cached=false;}
    }
    if(!cached) {
        report({L"Descargando Bodriosafío "+wide(channel.version)+L"…",1,{}});
        auto bytes=download_channel_file(channel.url,static_cast<size_t>(channel.size),cancelled);
        report({L"Verificando el archivo descargado…",2,{}});
        if(bytes.size()!=channel.size || sha256(bytes)!=channel.sha256) throw std::runtime_error("La descarga está incompleta o no coincide con la versión publicada. No se importó.");
        atomic_write(path,bytes);
    }
    if(cancelled()) throw std::runtime_error("Actualización cancelada.");
    report({L"Abriendo la importación en Minecraft…",3,{}});
    auto result=reinterpret_cast<INT_PTR>(ShellExecuteW(nullptr,L"open",path.c_str(),nullptr,nullptr,SW_SHOWNORMAL));
    if(result<=32) throw std::runtime_error("Windows no tiene asociada la importación de .mcaddon con Minecraft. El archivo verificado quedó en la carpeta updates del launcher.");
    log_line(L"Addon "+wide(channel.version)+L" descargado y verificado. Se abrió el importador de Minecraft; el launcher no confirma su finalización.");
    return L"Bodriosafío "+wide(channel.version)+L" verificado. Espera a que Minecraft termine la importación y después pulsa Jugar.";
}
void import_addon() {
    auto path=extract(202,L"Bodriosafio_0_3_15.mcaddon",addon_sha256);
    auto result=reinterpret_cast<INT_PTR>(ShellExecuteW(nullptr,L"open",path.c_str(),nullptr,nullptr,SW_SHOWNORMAL));
    if (result<=32) throw std::runtime_error("Windows no tiene asociada la importación de .mcaddon con Minecraft. El archivo está en la carpeta runtime del launcher.");
    log_line(L"El usuario solicitó importar Bodriosafio 0.3.15 mediante Minecraft.");
}
void show_credits() { open_document(203,L"CREDITOS_Y_LICENCIAS.txt"); }
void show_help() { open_document(204,L"LEEME_LAUNCHER.txt"); }
std::wstring explain_exception(const std::exception& error) { return wide(error.what()); }

namespace {
fs::path launcher_executable_path() {
    std::wstring out(32768,L'\0');DWORD n=GetModuleFileNameW(nullptr,out.data(),static_cast<DWORD>(out.size()));
    if(!n || n>=out.size()) fail(L"No se pudo localizar el launcher");
    out.resize(n);return fs::path(out);
}
void start_launcher_process(const fs::path& path,const std::vector<std::wstring>& args) {
    std::wstring command=launcher_argument(path.wstring());
    for(const auto& a:args) command+=L" "+launcher_argument(a);
    STARTUPINFOW startup{};startup.cb=sizeof startup;PROCESS_INFORMATION process{};
    if(!CreateProcessW(path.c_str(),command.data(),nullptr,nullptr,FALSE,0,nullptr,path.parent_path().c_str(),&startup,&process))
        fail(L"No se pudo iniciar la actualización del launcher");
    CloseHandle(process.hThread);CloseHandle(process.hProcess);
}
void validate_launcher_file(const fs::path& path,const std::string& expected,const std::string& version={}) {
    auto bytes=read_bytes(path,64*1024*1024);
    if(!valid_launcher_hash(expected) || sha256(bytes)!=expected) throw std::runtime_error("El ejecutable del launcher cambió después de verificarlo.");
    auto pe=pe_detail::u32(bytes,0x3c);
    if(pe_detail::u16(bytes,0)!=0x5a4d || pe_detail::u32(bytes,pe)!=0x4550 || pe_detail::u16(bytes,pe+4)!=IMAGE_FILE_MACHINE_AMD64)
        throw std::runtime_error("La descarga no es un launcher Windows x64 válido.");
    if(!version.empty()) {
        auto actual=read_game_version(path);auto wanted=parse_update_version(version);
        if(actual.a!=wanted[0] || actual.b!=wanted[1] || actual.c!=wanted[2] || actual.d!=0)
            throw std::runtime_error("La versión del ejecutable descargado no coincide con la publicada.");
    }
}
}
std::wstring available_launcher_version() {
    auto r=parse_launcher_release(download_channel_file(launcher_update_url,128*1024,[]{return false;}));
    return parse_update_version(r.version)>parse_update_version(launcher_version)?wide(r.version):std::wstring{};
}
LauncherUpdate prepare_launcher_update(const Reporter& report,const std::function<bool()>& cancelled) {
    report({L"Buscando una actualización del launcher…",0,{}});
    auto release=parse_launcher_release(download_channel_file(launcher_update_url,128*1024,cancelled));
    if(parse_update_version(release.version)<=parse_update_version(launcher_version)) return {};
    auto path=data_root()/L"launcher-updates"/wide(release.version)/(L"Bodriosafio_Launcher_Windows_"+wide(release.version)+L".exe");
    bool cached=false;
    if(fs::is_regular_file(path)) {
        try {validate_launcher_file(path,release.sha256,release.version);cached=fs::file_size(path)==release.size;}catch(const std::exception&){}
    }
    if(!cached) {
        report({L"Descargando launcher "+wide(release.version)+L"…",1,{}});
        auto bytes=download_channel_file(release.url,static_cast<size_t>(release.size),cancelled);
        if(bytes.size()!=release.size || sha256(bytes)!=release.sha256) throw std::runtime_error("La descarga del launcher está incompleta o no coincide con la versión publicada.");
        if(cancelled()) throw std::runtime_error("Actualización cancelada.");
        atomic_write(path,bytes);validate_launcher_file(path,release.sha256,release.version);
    }
    report({L"Actualización del launcher verificada.",3,{}});
    return {path,release.version,release.sha256,true};
}
void install_launcher_update(const LauncherUpdate& update) {
    if(!update.available) return;
    validate_launcher_file(update.path,update.sha256,update.version);
    auto target=launcher_executable_path();auto old=sha256(read_bytes(target,64*1024*1024));
    // Retain the five-argument helper protocol used by launcher 0.1.3.
    start_launcher_process(update.path,{L"--apply-launcher-update",std::to_wstring(GetCurrentProcessId()),target.wstring(),wide(old),wide(update.sha256)});
}
int launcher_update_helper(int argc,wchar_t** argv) {
    if(argc<2 || std::wstring(argv[1])!=L"--apply-launcher-update") return -1;
    try {
        if(argc!=6) throw std::runtime_error("Los argumentos de actualización no son válidos.");
        std::wstring pid_text=argv[2];size_t used=0;auto pid_value=std::stoull(pid_text,&used);
        if(used!=pid_text.size() || !pid_value || pid_value>MAXDWORD || pid_value==GetCurrentProcessId()) throw std::runtime_error("El proceso anterior no es válido.");
        DWORD pid=static_cast<DWORD>(pid_value);
        fs::path source=launcher_executable_path(),target=argv[3];auto old= utf8(argv[4]),fresh=utf8(argv[5]);
        if(!target.is_absolute() || target.extension()!=L".exe" || _wcsicmp(source.c_str(),target.c_str())==0)
            throw std::runtime_error("La ruta de actualización no es válida.");
        validate_launcher_file(source,fresh);validate_launcher_file(target,old);
        Handle parent(OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION|SYNCHRONIZE,FALSE,pid));
        if(parent) {
            if(!same_owner(parent.h)) throw std::runtime_error("El launcher anterior pertenece a otra sesión de usuario.");
            std::wstring parent_path(32768,L'\0');DWORD length=static_cast<DWORD>(parent_path.size());
            if(!QueryFullProcessImageNameW(parent.h,0,parent_path.data(),&length)) fail(L"No se pudo comprobar el launcher anterior");
            parent_path.resize(length);
            if(_wcsicmp(parent_path.c_str(),target.c_str())!=0) throw std::runtime_error("El proceso anterior no coincide con el launcher que se va a actualizar.");
            if(WaitForSingleObject(parent.h,45000)!=WAIT_OBJECT_0) throw std::runtime_error("El launcher anterior no terminó de cerrar; no se reemplazó.");
        } else if(GetLastError()!=ERROR_INVALID_PARAMETER) fail(L"No se pudo comprobar el cierre del launcher anterior");
        validate_launcher_file(target,old);
        auto suffix=L"."+std::to_wstring(GetCurrentProcessId())+L"."+std::to_wstring(GetTickCount64());
        fs::path staged=target;staged+=L".nuevo"+suffix;
        fs::path backup=target;backup+=L".antes_launcher"+suffix+L".bak";
        if(!CopyFileW(source.c_str(),staged.c_str(),TRUE)) fail(L"No se pudo preparar el nuevo launcher");
        try {
            validate_launcher_file(staged,fresh);
            if(!CopyFileW(target.c_str(),backup.c_str(),TRUE)) fail(L"No se pudo conservar una copia del launcher anterior");
            if(!MoveFileExW(staged.c_str(),target.c_str(),MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH)) fail(L"No se pudo sustituir el launcher; el anterior se conservó");
        } catch(...) {DeleteFileW(staged.c_str());throw;}
        try {start_launcher_process(target,{});}
        catch(...) {
            MoveFileExW(backup.c_str(),target.c_str(),MOVEFILE_REPLACE_EXISTING|MOVEFILE_WRITE_THROUGH);
            try {start_launcher_process(target,{});}catch(...){}
            throw;
        }
        log_line(L"Launcher actualizado; copia anterior: "+backup.wstring());return 0;
    } catch(const std::exception& e) {
        auto message=explain_exception(e);log_line(L"Actualización del launcher: "+message);
        MessageBoxW(nullptr,message.c_str(),L"Actualización del launcher",MB_OK|MB_ICONINFORMATION);return 1;
    }
}
}
