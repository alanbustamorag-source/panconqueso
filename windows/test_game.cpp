#ifndef UNICODE
#define UNICODE
#endif
#include <windows.h>
LRESULT CALLBACK fixture_window(HWND h,UINT m,WPARAM w,LPARAM l) {
    if (m==WM_DESTROY) { PostQuitMessage(0); return 0; }
    return DefWindowProcW(h,m,w,l);
}
int WINAPI wWinMain(HINSTANCE instance,HINSTANCE,LPWSTR,int) {
    WNDCLASSW cls{};cls.lpfnWndProc=fixture_window;cls.hInstance=instance;cls.lpszClassName=L"BodrioTestGame";
    RegisterClassW(&cls);
    HWND h=CreateWindowW(cls.lpszClassName,L"Test fixture — no es Minecraft",WS_OVERLAPPEDWINDOW,0,0,500,250,nullptr,nullptr,instance,nullptr);
    ShowWindow(h,SW_SHOWMINNOACTIVE);
    MSG message{};while(GetMessageW(&message,nullptr,0,0)>0) {TranslateMessage(&message);DispatchMessageW(&message);}
    return 0;
}
