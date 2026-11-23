#include "backend.hpp"
#include "native_exports.hpp"
#include <tlhelp32.h>
#include <fstream>
#include <iostream>
#include <vector>

namespace fs=std::filesystem;
static unsigned checks=0;
void check(bool yes,const char* label) {
    ++checks; if(!yes) {std::cerr<<"FAIL: "<<label<<std::endl;std::exit(1);}
}
std::string read(const fs::path& p) {std::ifstream f(p,std::ios::binary);return {std::istreambuf_iterator<char>(f),{}};}
DWORD init_calls(DWORD pid,const fs::path& dll) {
    HANDLE snapshot=CreateToolhelp32Snapshot(TH32CS_SNAPMODULE|TH32CS_SNAPMODULE32,pid);
    MODULEENTRY32W entry{};entry.dwSize=sizeof entry;uintptr_t base=0;
    if(Module32FirstW(snapshot,&entry)) do {if(_wcsicmp(entry.szModule,L"BodriosafioEffects.dll")==0)base=reinterpret_cast<uintptr_t>(entry.modBaseAddr);}while(Module32NextW(snapshot,&entry));
    CloseHandle(snapshot);check(base!=0,"Anticombustion DLL loaded by the ordinary prepare path");
    HANDLE process=OpenProcess(PROCESS_CREATE_THREAD|PROCESS_QUERY_INFORMATION|PROCESS_VM_OPERATION|PROCESS_VM_READ|PROCESS_VM_WRITE,FALSE,pid);
    auto rva=bodrio::native_export_rva(read(dll),"BodriosafioTestInitCalls");
    HANDLE thread=CreateRemoteThread(process,nullptr,0,reinterpret_cast<LPTHREAD_START_ROUTINE>(base+rva),nullptr,0,nullptr);
    check(thread!=nullptr && WaitForSingleObject(thread,10000)==WAIT_OBJECT_0,"read initializer count from own fixture");
    DWORD result=0;GetExitCodeThread(thread,&result);CloseHandle(thread);CloseHandle(process);return result;
}
struct Fixture {
    fs::path dir;
    PROCESS_INFORMATION process{};
    Fixture(const fs::path& source,const fs::path& destination) : dir(destination.parent_path()) {
        fs::create_directories(dir);fs::copy_file(source,destination,fs::copy_options::overwrite_existing);
        std::wstring command=L"\""+destination.wstring()+L"\"";
        STARTUPINFOW startup{};startup.cb=sizeof(startup);
        check(CreateProcessW(destination.c_str(),command.data(),nullptr,nullptr,FALSE,0,nullptr,dir.c_str(),&startup,&process)!=0,"start fixture");
        WaitForInputIdle(process.hProcess,5000);
    }
    ~Fixture() {
        // Terminate only our own disposable test fixture, never a discovered game.
        TerminateProcess(process.hProcess,0);WaitForSingleObject(process.hProcess,5000);
        CloseHandle(process.hThread);CloseHandle(process.hProcess);
    }
};
int wmain(int argc,wchar_t** argv) {
    if(argc!=4) return 2;
    auto root=fs::temp_directory_path()/(L"BodrioIntegration_"+std::to_wstring(GetCurrentProcessId()));
    auto report=[](const bodrio::Progress&){};
    {
        Fixture game(fs::absolute(argv[1]),root/L"valid"/L"Minecraft.Windows.exe");
        auto config=game.dir/L"BuildLimitChanger"/L"config.ini";
        fs::create_directories(config.parent_path());
        {std::ofstream f(config);f<<"[Overworld]\nmin=-128\nmax=512\n[Nether]\nmin=0\nmax=128\n[Extra]\nmin=0\nmax=1024\n";}
        auto result=bodrio::prepare_running_game(game.process.dwProcessId,report);
        check(result.version==L"1.26.60.5","read real Windows version resource");
        check(result.display_version==L"26.60.5","format actual version from Windows resource");
        check(result.effects_prepared,"Jugar prepares Anticombustion without an external activator");
        check(init_calls(game.process.dwProcessId,fs::absolute(argv[3]))==3,"retry native initialization until engine readiness");
        check(result.config==config,"resolve non-packaged config like native module");
        check(!result.already_loaded && result.config_changed,"first load and config change detected");
        auto contents=read(config);
        check(contents.find("max=256")!=std::string::npos,"write real Nether profile");
        check(contents.find("min=-128\nmax=512")!=std::string::npos,"retain Overworld values");
        check(contents.find("[Extra]\nmin=0\nmax=1024")!=std::string::npos,"retain custom dimensions");
        unsigned backups=0;for(const auto& item:fs::directory_iterator(config.parent_path())) if(item.path().extension()==L".bak") ++backups;
        check(backups==1,"backup config once before write");
        auto again=bodrio::prepare_running_game(game.process.dwProcessId,report);
        check(again.effects_prepared,"second Jugar preserves integrated effects");
        check(init_calls(game.process.dwProcessId,fs::absolute(argv[3]))==4,"loaded DLL reused on second preparation");
        check(again.already_loaded && !again.config_changed,"recognize loaded official DLL and avoid duplicate hook");
        unsigned after=0;for(const auto& item:fs::directory_iterator(config.parent_path())) if(item.path().extension()==L".bak") ++after;
        check(after==1,"no redundant backup on second launch");
    }
    {
        Fixture wrong(fs::absolute(argv[2]),root/L"unsupported"/L"Minecraft.Windows.exe");
        std::wstring reported;
        auto capture=[&](const bodrio::Progress& p) {if(!p.detected_version.empty()) reported=p.detected_version;};
        bool rejected=false;try {bodrio::prepare_running_game(wrong.process.dwProcessId,capture);} catch (...) {rejected=true;}
        check(rejected,"refuse other game version");
        check(reported==L"26.61","publish actual incompatible version before rejecting");
        check(!fs::exists(wrong.dir/L"BuildLimitChanger"),"refuse before configuration mutation");
    }
    {
        Fixture wrong(fs::absolute(argv[1]),root/L"wrong_name"/L"AnotherApplication.exe");
        bool rejected=false;try {bodrio::prepare_running_game(wrong.process.dwProcessId,report);} catch (...) {rejected=true;}
        check(rejected,"refuse unrelated process");
        check(!fs::exists(wrong.dir/L"BuildLimitChanger"),"no configuration for unrelated process");
    }
    fs::remove_all(root);
    std::cout<<checks<<" Windows integration checks passed (test fixtures, not Minecraft)"<<std::endl;
    return 0;
}
