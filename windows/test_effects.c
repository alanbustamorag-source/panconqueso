#include <windows.h>
static LONG calls;
__declspec(dllexport) DWORD WINAPI BodriosafioInitialize(void *unused) {
    (void)unused;return InterlockedIncrement(&calls)<3?ERROR_RETRY:ERROR_SUCCESS;
}
__declspec(dllexport) DWORD WINAPI BodriosafioEffectState(void *unused) {(void)unused;return calls>=3?1:0;}
__declspec(dllexport) DWORD WINAPI BodriosafioTestInitCalls(void *unused) {(void)unused;return calls;}
BOOL WINAPI DllMain(HINSTANCE instance,DWORD reason,void *reserved) {(void)instance;(void)reason;(void)reserved;return TRUE;}
