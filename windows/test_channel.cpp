#include "update_channel.hpp"
#include <fstream>
#include <sstream>
#include <iostream>
#include <functional>
int main(int argc,char** argv) {
    if(argc!=2) return 2;
    int checks=0;
    auto check=[&](bool ok,const char* message){if(!ok) throw std::runtime_error(message);++checks;};
    auto rejects=[&](const std::function<void()>& f){bool rejected=false;try{f();}catch(const std::exception&){rejected=true;}check(rejected,"expected rejection");};
    rejects([&]{bodrio::validate_update_url("https://raw.githubusercontent.com/Bnnnlss/bodriosafio-updates/main/x.mcaddon");});
    std::ifstream in(argv[1]);std::ostringstream stream;stream<<in.rdbuf();auto text=stream.str();
    auto parsed=bodrio::parse_update_channel(text);
    check(parsed.version=="0.3.13","version");
    check(parsed.size==410652,"size");
    check(parsed.behavior==bodrio::PackVersion{0,3,13},"BP");
    check(parsed.resource==bodrio::PackVersion{1,5,13},"RP");
    check(parsed.sha256=="ebebda28ee003acba75da6970aca4d6705cabeda93ae7c26d99a97f071375437","digest");
    rejects([&]{bodrio::parse_update_channel(text,"windows","0.1.1");});
    rejects([&]{bodrio::validate_update_url("http://raw.githubusercontent.com/alanbustamorag-source/panconqueso/a");});
    rejects([&]{bodrio::validate_update_url("https://raw.githubusercontent.com/other/repo/a");});
    rejects([&]{bodrio::validate_update_url("https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/../a");});
    rejects([&]{bodrio::validate_update_url("https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/%2e%2e/a");});
    rejects([&]{bodrio::parse_update_version("0.1.2.3");});
    rejects([&]{bodrio::parse_update_version("0.-1.2");});
    auto original=nlohmann::json::parse(text);
    for(const char* field:{"schema","size","sha","uuid","fraction","minecraft","duplicate"}) {
        auto j=original;
        if(std::string(field)=="schema")j["schema"]=2;
        if(std::string(field)=="size")j["addon"]["size"]=64*1024*1024+1;
        if(std::string(field)=="sha")j["addon"]["sha256"]="invalid";
        if(std::string(field)=="uuid")j["packs"][0]["uuid"]="foreign";
        if(std::string(field)=="fraction")j["packs"][0]["version"][2]=13.5;
        if(std::string(field)=="minecraft")j["minecraft"]="26.61";
        if(std::string(field)=="duplicate")j["packs"][1]=j["packs"][0];
        rejects([&]{bodrio::parse_update_channel(j.dump());});
    }
    std::cout<<"PASS: "<<checks<<" Windows update channel checks\n";
}

