#include "backend.hpp"
#include <shellapi.h>
#include <dwmapi.h>
#include <commctrl.h>
#include <atomic>
#include <thread>
#include <memory>
#include <algorithm>

namespace {
constexpr UINT update_message=WM_APP+41;
constexpr UINT version_message=WM_APP+42;
constexpr int play_id=100, addon_id=101, log_id=102, credits_id=103, help_id=104, launcher_id=105;
const COLORREF background=RGB(14,23,20),surface=RGB(25,39,32),border=RGB(49,69,58);
const COLORREF white=RGB(234,243,237),muted=RGB(167,188,174),green=RGB(157,227,178),ink=RGB(15,35,23);
HWND window_handle=nullptr,play_button=nullptr;
HFONT body_font=nullptr,title_font=nullptr,small_font=nullptr,button_font=nullptr,headline_font=nullptr;
HBRUSH background_brush=nullptr;
float scale=1.0f;
std::thread worker;
std::thread version_reader;
std::atomic<bool> cancel_requested{false};
bool busy=false,close_after_work=false,prepared=false,has_error=false,uncertain_load=false;
std::wstring status=L"Pulsa Jugar para iniciar Minecraft.";
std::wstring detected=L"Buscando…";
bool received_launch_version=false;
bool updating_addon=false;
bool updating_launcher=false;
int stage=0;
struct Update {
    std::wstring text;
    int step=0;
    bool finished=false,success=false;
    std::wstring version;
    bool uncertain=false;
    bool addon=false;
    bool launcher=false;
    std::shared_ptr<bodrio::LauncherUpdate> launcher_update={};
};
int px(int value) { return static_cast<int>(value*scale+0.5f); }
RECT box(int x,int y,int w,int h) { return {px(x),px(y),px(x+w),px(y+h)}; }
HFONT font(int size,int weight) {
    return CreateFontW(-px(size),0,0,0,weight,FALSE,FALSE,FALSE,DEFAULT_CHARSET,OUT_DEFAULT_PRECIS,
                      CLIP_DEFAULT_PRECIS,CLEARTYPE_QUALITY,DEFAULT_PITCH,L"Segoe UI");
}
void text(HDC dc,const std::wstring& value,RECT rect,HFONT face,COLORREF color,UINT format=DT_LEFT|DT_TOP|DT_WORDBREAK|DT_NOPREFIX) {
    auto old=SelectObject(dc,face);
    SetBkMode(dc,TRANSPARENT); SetTextColor(dc,color);
    DrawTextW(dc,value.c_str(),static_cast<int>(value.size()),&rect,format);
    SelectObject(dc,old);
}
void rounded(HDC dc,RECT r,COLORREF fill,int radius=18) {
    auto brush=CreateSolidBrush(fill);
    auto old_brush=SelectObject(dc,brush);
    auto old_pen=SelectObject(dc,GetStockObject(NULL_PEN));
    RoundRect(dc,r.left,r.top,r.right,r.bottom,px(radius),px(radius));
    SelectObject(dc,old_brush); SelectObject(dc,old_pen); DeleteObject(brush);
}
void send(Update* u) {
    if (!PostMessageW(window_handle,update_message,0,reinterpret_cast<LPARAM>(u))) delete u;
}
void inspect_version(HWND destination) {
    try {
        version_reader=std::thread([destination] {
            std::wstring actual;
            try { actual=bodrio::detect_game_version(); }
            catch (const std::exception& error) { bodrio::log_line(L"No se pudo detectar la versión al abrir: "+bodrio::explain_exception(error)); }
            auto message=new std::wstring(actual.empty() ? L"Sin detectar" : actual);
            if (!PostMessageW(destination,version_message,0,reinterpret_cast<LPARAM>(message))) delete message;
            try {
                auto addon_version=new std::wstring(bodrio::available_addon_version());
                if(!PostMessageW(destination,version_message,1,reinterpret_cast<LPARAM>(addon_version))) delete addon_version;
            } catch(const std::exception& error) { bodrio::log_line(L"No se pudo comprobar el addon al abrir: "+bodrio::explain_exception(error)); }
        });
    } catch (const std::exception&) { detected=L"Sin detectar"; }
}
void set_busy(bool value) {
    busy=value;
    EnableWindow(play_button,!value && !uncertain_load);
    EnableWindow(GetDlgItem(window_handle,addon_id),!value);
    EnableWindow(GetDlgItem(window_handle,launcher_id),!value);
    SetWindowTextW(play_button,value ? (updating_addon || updating_launcher ? L"Actualizando…" : L"Preparando…") : prepared ? L"Comprobar y abrir" : L"Jugar");
    InvalidateRect(window_handle,nullptr,FALSE);
}
void start() {
    if (busy || uncertain_load) return;
    if (worker.joinable()) worker.join();
    cancel_requested=false; has_error=false; prepared=false;
    set_busy(true);
    worker=std::thread([] {
        try {
            auto result=bodrio::launch_and_prepare([](const bodrio::Progress& p) {
                send(new Update{p.text,p.step,false,false,p.detected_version});
            },[] { return cancel_requested.load(); });
            std::wstring done=result.effects_prepared
                ? L"Listo: Nether y Anticombustión preparados. Abre tu mundo desde Minecraft."
                : L"Minecraft abierto en modo addon. Importa y activa los packs; Nether ampliado y el efecto nativo quedan pendientes para esta versión.";
            if (result.config_changed) done+=L" Si el mundo estaba abierto, sal al menú y vuelve a entrar.";
            send(new Update{done,4,true,true,result.display_version});
        } catch (const bodrio::LoadUncertain& e) {
            auto message=bodrio::explain_exception(e);
            bodrio::log_line(L"CARGA SIN CONFIRMAR: "+message);
            send(new Update{message,0,true,false,{},true});
        } catch (const std::exception& e) {
            auto message=bodrio::explain_exception(e);
            bodrio::log_line(L"ERROR: "+message);
            send(new Update{message,0,true,false,{}});
        } catch (...) {
            send(new Update{L"No se pudo completar la preparación. Abre el registro para revisar el problema.",0,true,false,{}});
        }
    });
}
void show_error(const std::exception& e) {
    auto message=bodrio::explain_exception(e);
    bodrio::log_line(L"ERROR: "+message);
    MessageBoxW(window_handle,message.c_str(),L"Bodriosafío",MB_OK|MB_ICONINFORMATION);
}
void start_addon_update() {
    if(busy) return;
    if(worker.joinable()) worker.join();
    cancel_requested=false;has_error=false;updating_addon=true;set_busy(true);
    worker=std::thread([] {
        try {
            auto message=bodrio::update_addon([](const bodrio::Progress& p) {
                send(new Update{p.text,p.step,false,false,{},false,true});
            },[]{return cancel_requested.load();});
            send(new Update{message,4,true,true,{},false,true});
        } catch(const std::exception& error) {
            auto message=bodrio::explain_exception(error);bodrio::log_line(L"Actualización del addon: "+message);
            send(new Update{message,0,true,false,{},false,true});
        } catch(...) {send(new Update{L"No se pudo completar la actualización del addon.",0,true,false,{},false,true});}
    });
}
void start_launcher_update() {
    if(busy) return;
    if(worker.joinable()) worker.join();
    cancel_requested=false;has_error=false;updating_launcher=true;set_busy(true);
    worker=std::thread([] {
        try {
            auto release=std::make_shared<bodrio::LauncherUpdate>(bodrio::prepare_launcher_update([](const bodrio::Progress& p) {
                auto u=new Update{};u->text=p.text;u->step=p.step;u->launcher=true;send(u);
            },[]{return cancel_requested.load();}));
            auto u=new Update{};u->text=release->available?L"Nueva versión del launcher preparada.":L"El launcher 0.1.5 ya está actualizado.";
            u->step=4;u->finished=true;u->success=true;u->launcher=true;u->launcher_update=release;send(u);
        } catch(const std::exception& e) {
            auto u=new Update{};u->text=bodrio::explain_exception(e);u->finished=true;u->launcher=true;
            bodrio::log_line(L"Actualización del launcher: "+u->text);send(u);
        } catch(...) {
            auto u=new Update{};u->text=L"No se pudo completar la actualización del launcher.";u->finished=true;u->launcher=true;send(u);
        }
    });
}
void button(HWND parent,int id,const wchar_t* label,int x,int y,int w,int h) {
    HWND control=CreateWindowExW(0,L"BUTTON",label,WS_CHILD|WS_VISIBLE|WS_TABSTOP|BS_OWNERDRAW,
                                px(x),px(y),px(w),px(h),parent,reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)),nullptr,nullptr);
    SendMessageW(control,WM_SETFONT,reinterpret_cast<WPARAM>(button_font),TRUE);
    if (id==play_id) play_button=control;
}
void paint(HDC dc) {
    RECT client{}; GetClientRect(window_handle,&client); FillRect(dc,&client,background_brush);
    rounded(dc,box(32,27,48,48),green,12);
    text(dc,L"B",box(32,30,48,44),headline_font,ink,DT_CENTER|DT_VCENTER|DT_SINGLELINE);
    text(dc,L"BODRIOSAFÍO",box(96,25,400,33),title_font,white);
    text(dc,L"LAUNCHER PARA WINDOWS",box(98,62,450,22),small_font,muted);
    text(dc,L"Tu próxima partida empieza acá.",box(32,113,780,42),headline_font,white);
    text(dc,L"Minecraft Bedrock · Windows x64",box(33,161,770,27),body_font,muted);
    rounded(dc,box(32,210,784,116),surface);
    text(dc,L"MINECRAFT",box(53,226,730,22),small_font,muted);
    text(dc,L"Versiones objetivo: 26.60 / 26.62 / 26.63",box(53,252,730,27),body_font,white);
    text(dc,L"Versión actual: "+detected,box(53,284,730,27),body_font,white);
    auto status_area=box(33,351,780,64);
    text(dc,status,status_area,body_font,has_error ? RGB(255,191,159) : white);
    if (busy) {
        auto track=box(32,430,784,4); rounded(dc,track,border,4);
        int w=std::max(35,(stage+1)*156); if(w>784) w=784;
        rounded(dc,box(32,430,w,4),green,4);
    }
}
LRESULT CALLBACK proc(HWND hwnd,UINT msg,WPARAM wp,LPARAM lp) {
    switch(msg) {
    case WM_CREATE: {
        window_handle=hwnd;
        body_font=font(16,FW_NORMAL);title_font=font(28,FW_BOLD);small_font=font(12,FW_NORMAL);
        button_font=font(15,FW_SEMIBOLD);headline_font=font(29,FW_SEMIBOLD);
        background_brush=CreateSolidBrush(background);
        button(hwnd,play_id,L"Jugar",32,459,176,48);
        button(hwnd,addon_id,L"Actualizar addon",220,459,158,48);
        button(hwnd,launcher_id,L"Actualizar launcher",390,459,176,48);
        button(hwnd,log_id,L"Registro",578,459,76,48);
        button(hwnd,help_id,L"Ayuda",666,459,68,48);
        button(hwnd,credits_id,L"Créditos",746,459,70,48);
        BOOL dark=TRUE; DwmSetWindowAttribute(hwnd,20,&dark,sizeof(dark));
        inspect_version(hwnd);
        return 0;
    }
    case WM_COMMAND:
        if (HIWORD(wp)!=BN_CLICKED) break;
        try {
            switch(LOWORD(wp)) {
            case play_id: start(); break;
            case addon_id:
                start_addon_update(); break;
            case launcher_id: start_launcher_update();break;
            case credits_id: bodrio::show_credits(); break;
            case help_id:
                MessageBoxW(hwnd,
                    L"Bodriosafío Launcher\nVersión 0.1.5 · Anticombustión integrada\n\n"
                    L"Minecraft objetivo: 26.60 / 26.62 / 26.63 (Windows x64). En hotfix nuevos se usa modo addon sin nativos no validados.\n\n"
                    L"Jugar abre Minecraft y prepara automáticamente el Nether y Anticombustión.\n"
                    L"Actualizar addon busca la última versión, verifica la descarga y abre su importación en Minecraft.\n"
                    L"Incluye el addon corregido 0.3.15 y evita volver a una versión anterior del canal.\n"
                    L"Actualizar launcher descarga su nueva versión desde GitHub. Al confirmar, conserva una copia del anterior y lo reabre.\n"
                    L"Espera a que Minecraft termine de importar los packs antes de pulsar Jugar.\n"
                    L"Registro muestra los detalles si aparece un error.\n\n"
                    L"La guía completa está en LEEME.txt, incluido con el launcher.",
                    L"Ayuda",MB_OK|MB_ICONINFORMATION);
                break;
            case log_id: {
                bodrio::log_line(L"El usuario abrió el registro local.");
                auto path=bodrio::log_path();
                if (reinterpret_cast<INT_PTR>(ShellExecuteW(hwnd,L"open",path.c_str(),nullptr,nullptr,SW_SHOWNORMAL))<=32)
                    MessageBoxW(hwnd,path.c_str(),L"Ubicación del registro",MB_OK);
                break;
            }
            }
        } catch(const std::exception& e) { show_error(e); }
        return 0;
    case update_message: {
        std::unique_ptr<Update> update(reinterpret_cast<Update*>(lp));
        stage=update->step;
        status=update->text;
        if (!update->version.empty()) {
            detected=update->version;
            received_launch_version=true;
        }
        if (update->finished) {
            if (worker.joinable()) worker.join();
            has_error=!update->success;
            if(!update->addon && !update->launcher) {prepared=update->success;uncertain_load=update->uncertain;}
            updating_addon=false;updating_launcher=false;
            set_busy(false);
            if (close_after_work) DestroyWindow(hwnd);
            else if(update->launcher && update->success && update->launcher_update && update->launcher_update->available) {
                auto prompt=L"Se descargó el launcher "+std::wstring(update->launcher_update->version.begin(),update->launcher_update->version.end())
                    +L". ¿Reiniciar para instalarlo? Se conservará una copia del ejecutable anterior.";
                if(MessageBoxW(hwnd,prompt.c_str(),L"Actualizar launcher",MB_YESNO|MB_ICONINFORMATION)==IDYES) {
                    try {bodrio::install_launcher_update(*update->launcher_update);DestroyWindow(hwnd);}
                    catch(const std::exception& e) {show_error(e);}
                }
            } else if (!update->success) {
                if(update->addon) {
                    std::wstring message=update->text+L"\n\n¿Quieres importar la versión incluida (0.3.15)?";
                    if(MessageBoxW(hwnd,message.c_str(),L"Actualización del addon",MB_YESNO|MB_ICONINFORMATION|MB_DEFBUTTON2)==IDYES) {
                        try {bodrio::import_addon();} catch(const std::exception& error) {show_error(error);}
                    }
                } else MessageBoxW(hwnd,update->text.c_str(),L"No se completó la preparación",MB_OK|MB_ICONINFORMATION);
            }
        }
        InvalidateRect(hwnd,nullptr,FALSE);
        return 0;
    }
    case version_message: {
        std::unique_ptr<std::wstring> actual(reinterpret_cast<std::wstring*>(lp));
        if(wp==1) {
            if(!busy && !prepared && !has_error) status=L"Addon disponible: "+*actual+L". Pulsa Actualizar addon para instalarlo.";
        } else if (!received_launch_version) detected=*actual;
        InvalidateRect(hwnd,nullptr,FALSE);
        return 0;
    }
    case WM_CTLCOLORBTN:
        SetBkColor(reinterpret_cast<HDC>(wp),background);
        return reinterpret_cast<LRESULT>(background_brush);
    case WM_DRAWITEM: {
        auto item=reinterpret_cast<DRAWITEMSTRUCT*>(lp);
        if(item->CtlType!=ODT_BUTTON) break;
        bool primary=item->CtlID==play_id,disabled=(item->itemState&ODS_DISABLED)!=0;
        // Clear the full native button rectangle before drawing rounded corners.
        // Otherwise the default white background remains visible at the corners.
        FillRect(item->hDC,&item->rcItem,background_brush);
        COLORREF fill=primary ? (disabled ? border : green) : surface;
        if((item->itemState&ODS_SELECTED) && !disabled) fill=primary ? RGB(126,198,148) : border;
        rounded(item->hDC,item->rcItem,fill,12);
        wchar_t label[128]{};GetWindowTextW(item->hwndItem,label,128);
        text(item->hDC,label,item->rcItem,button_font,primary ? ink : disabled ? muted : white,DT_CENTER|DT_VCENTER|DT_SINGLELINE|DT_NOPREFIX);
        if(item->itemState&ODS_FOCUS) {
            RECT r=item->rcItem;InflateRect(&r,-px(3),-px(3));
            HPEN pen=CreatePen(PS_SOLID,std::max(1,px(1)),primary ? ink : green);
            auto old_pen=SelectObject(item->hDC,pen);
            auto old_brush=SelectObject(item->hDC,GetStockObject(HOLLOW_BRUSH));
            RoundRect(item->hDC,r.left,r.top,r.right,r.bottom,px(10),px(10));
            SelectObject(item->hDC,old_brush);SelectObject(item->hDC,old_pen);DeleteObject(pen);
        }
        return TRUE;
    }
    case WM_PAINT: {
        PAINTSTRUCT ps{};HDC dc=BeginPaint(hwnd,&ps);
        RECT client{};GetClientRect(hwnd,&client);
        HDC memory=CreateCompatibleDC(dc);HBITMAP bitmap=CreateCompatibleBitmap(dc,client.right,client.bottom);
        auto old=SelectObject(memory,bitmap);paint(memory);
        BitBlt(dc,0,0,client.right,client.bottom,memory,0,0,SRCCOPY);
        SelectObject(memory,old);DeleteObject(bitmap);DeleteDC(memory);EndPaint(hwnd,&ps);return 0;
    }
    case WM_ERASEBKGND: return 1;
    case WM_CLOSE:
        if(busy) { close_after_work=true;cancel_requested=true;status=L"Terminando la operación en curso…";InvalidateRect(hwnd,nullptr,FALSE);return 0; }
        DestroyWindow(hwnd);return 0;
    case WM_DESTROY:
        DeleteObject(body_font);DeleteObject(title_font);DeleteObject(small_font);DeleteObject(button_font);DeleteObject(headline_font);DeleteObject(background_brush);
        PostQuitMessage(0);return 0;
    }
    return DefWindowProcW(hwnd,msg,wp,lp);
}
}

int WINAPI wWinMain(HINSTANCE instance,HINSTANCE,LPWSTR,int show) {
    int argc=0;wchar_t** argv=CommandLineToArgvW(GetCommandLineW(),&argc);
    if(argv) {int applied=bodrio::launcher_update_helper(argc,argv);LocalFree(argv);if(applied>=0) return applied;}
    SetProcessDPIAware();
    HDC screen=GetDC(nullptr);scale=GetDeviceCaps(screen,LOGPIXELSX)/96.0f;ReleaseDC(nullptr,screen);
    HANDLE single=CreateMutexW(nullptr,FALSE,L"Local\\BodriosafioLauncher_0_1_0");
    if (!single) return 1;
    if(GetLastError()==ERROR_ALREADY_EXISTS) {
        MessageBoxW(nullptr,L"El launcher ya está abierto.",L"Bodriosafío",MB_OK|MB_ICONINFORMATION);CloseHandle(single);return 0;
    }
    WNDCLASSEXW cls{};cls.cbSize=sizeof(cls);cls.lpfnWndProc=proc;cls.hInstance=instance;
    cls.hCursor=LoadCursorW(nullptr,IDC_ARROW);cls.lpszClassName=L"BodriosafioLauncher010";
    cls.hIcon=LoadIconW(instance,MAKEINTRESOURCEW(1));cls.hIconSm=cls.hIcon;
    if(!RegisterClassExW(&cls)) {CloseHandle(single);return 1;}
    RECT size=box(0,0,848,539);
    DWORD style=WS_OVERLAPPED|WS_CAPTION|WS_SYSMENU|WS_MINIMIZEBOX|WS_CLIPCHILDREN;
    AdjustWindowRectEx(&size,style,FALSE,0);
    HWND hwnd=CreateWindowExW(0,cls.lpszClassName,L"Bodriosafío Launcher",style,CW_USEDEFAULT,CW_USEDEFAULT,
                              size.right-size.left,size.bottom-size.top,nullptr,nullptr,instance,nullptr);
    if(!hwnd) {CloseHandle(single);return 1;}
    ShowWindow(hwnd,show);UpdateWindow(hwnd);
    MSG msg{};
    while(GetMessageW(&msg,nullptr,0,0)>0) {
        if(!IsDialogMessageW(hwnd,&msg)) {TranslateMessage(&msg);DispatchMessageW(&msg);}
    }
    if(worker.joinable()) worker.join();
    if(version_reader.joinable()) version_reader.join();
    CloseHandle(single);
    return static_cast<int>(msg.wParam);
}
