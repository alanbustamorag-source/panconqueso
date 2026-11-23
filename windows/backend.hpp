#pragma once
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define NOMINMAX
#include <windows.h>
#include <filesystem>
#include <functional>
#include <string>
#include <stdexcept>

namespace bodrio {
class LoadUncertain : public std::runtime_error {
public:
    using std::runtime_error::runtime_error;
};
struct Progress { std::wstring text; int step; std::wstring detected_version = {}; };
using Reporter = std::function<void(const Progress&)>;
struct LaunchResult {
    std::wstring version;
    std::filesystem::path config;
    bool already_loaded = false;
    bool config_changed = false;
    std::wstring display_version;
    bool effects_prepared = false;
};
std::filesystem::path data_root();
std::filesystem::path log_path();
void log_line(const std::wstring& text);
std::wstring detect_game_version();
LaunchResult launch_and_prepare(const Reporter& report, const std::function<bool()>& cancelled);
void import_addon();
std::wstring available_addon_version();
std::wstring update_addon(const Reporter& report, const std::function<bool()>& cancelled);
void show_credits();
void show_help();
struct LauncherUpdate {
    std::filesystem::path path;
    std::string version,sha256;
    bool available=false;
};
std::wstring available_launcher_version();
LauncherUpdate prepare_launcher_update(const Reporter& report,const std::function<bool()>& cancelled);
void install_launcher_update(const LauncherUpdate& update);
int launcher_update_helper(int argc,wchar_t** argv);
std::wstring explain_exception(const std::exception& error);

// Used by the Windows integration harness; the production UI accepts no PID or DLL arguments.
LaunchResult prepare_running_game(DWORD pid, const Reporter& report,const std::function<bool()>& cancelled=[] {return false;});
}
