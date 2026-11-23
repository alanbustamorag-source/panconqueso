#include "launcher_update.hpp"
#include "native_exports.hpp"
#include "profile.hpp"
#include <fstream>
#include <iostream>
#include <functional>
int main(int argc,char** argv) {
    if(argc!=2) return 2;
    unsigned checks=0;
    auto check=[&](bool yes,const char* description){++checks;if(!yes)throw std::runtime_error(description);};
    auto reject=[&](const std::function<void()>& fn){bool failed=false;try{fn();}catch(const std::exception&){failed=true;}check(failed,"expected invalid input to be refused");};
    std::ifstream in(argv[1],std::ios::binary);std::string dll{std::istreambuf_iterator<char>(in),{}};
    auto init=bodrio::native_export_rva(dll,"BodriosafioInitialize");
    auto state=bodrio::native_export_rva(dll,"BodriosafioEffectState");
    check(init>0 && state>0 && init!=state,"actual native exports resolve independently");
    reject([&]{bodrio::native_export_rva(dll,"UnknownInitialize");});
    reject([&]{bodrio::native_export_rva(dll.substr(0,128),"BodriosafioInitialize");});
    auto broken=dll;broken[0]=0;reject([&]{bodrio::native_export_rva(broken,"BodriosafioInitialize");});
    broken=dll;auto nt=bodrio::pe_detail::u32(dll,0x3c);broken[nt+4]=0;reject([&]{bodrio::native_export_rva(broken,"BodriosafioInitialize");});
    check(bodrio::supported_effects_version(1,26,60,5),"exact supplied native engine build");
    check(!bodrio::supported_effects_version(1,26,60,4),"Android-equivalent revision not accepted as Windows build");
    check(!bodrio::supported_effects_version(1,26,61,5),"future engine not accepted");
    check(bodrio::use_included_addon("0.3.13"),"old public feed cannot downgrade the bundled fix");
    check(bodrio::use_included_addon("0.3.14"),"experimental older addon cannot replace the fix");
    check(!bodrio::use_included_addon("0.3.15"),"current public release stays usable");
    check(!bodrio::use_included_addon("0.3.16"),"future public addon remains updateable");
    nlohmann::json channel={{"schema",1},{"project","bodriosafio"},{"kind","launcher"},{"platform","windows"},{"architecture","x64"},
        {"version","0.1.5"},{"url","https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/Bodriosafio_Launcher_Windows_0_1_5.exe"},
        {"sha256",std::string(64,'a')},{"size",12345}};
    auto valid=bodrio::parse_launcher_release(channel.dump());
    check(valid.version=="0.1.5" && valid.size==12345,"Windows launcher update feed parsed");
    for(const auto& field:{"platform","architecture","hash","size","url","extension","version"}) {
        auto j=channel;std::string key=field;
        if(key=="platform")j["platform"]="android";
        if(key=="architecture")j["architecture"]="arm64";
        if(key=="hash")j["sha256"]="wrong";
        if(key=="size")j["size"]=64*1024*1024+1;
        if(key=="url")j["url"]="https://example.com/test.exe";
        if(key=="extension")j["url"]="https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/test.zip";
        if(key=="version")j["version"]="0.1.4.7";
        reject([&]{bodrio::parse_launcher_release(j.dump());});
    }
    check(bodrio::launcher_argument(L"C:\\Games\\Bodrio launcher.exe")==L"\"C:\\Games\\Bodrio launcher.exe\"","spaces in launcher path are quoted");
    check(bodrio::launcher_argument(L"folder\\")==L"\"folder\\\\\"","trailing path slash preserved by Windows argument parser");
    check(bodrio::launcher_argument(L"a\"b")==L"\"a\\\"b\"","embedded quote is escaped");
    std::cout<<checks<<" integrated launcher and update contract checks passed\n";
}
