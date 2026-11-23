#pragma once
#include "update_channel.hpp"
#include <filesystem>

namespace bodrio {
inline constexpr const char* launcher_version="0.1.5";
inline constexpr const char* included_addon_version="0.3.15";
inline constexpr const char* launcher_update_url="https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/launcher-windows-latest.json";
inline bool use_included_addon(const std::string& published) {
    return parse_update_version(published)<parse_update_version(included_addon_version);
}
struct LauncherRelease { std::string version,url,sha256,notes; std::uint64_t size=0; };
inline bool valid_launcher_hash(const std::string& value) {
    return value.size()==64 && std::all_of(value.begin(),value.end(),[](unsigned char c){return (c>='0'&&c<='9')||(c>='a'&&c<='f');});
}
inline LauncherRelease parse_launcher_release(const std::string& text) {
    if(text.empty() || text.size()>128*1024) throw std::runtime_error("El canal del launcher tiene un tamaño no válido.");
    auto j=nlohmann::json::parse(text);
    if(j.at("schema")!=1 || j.at("project")!="bodriosafio" || j.at("kind")!="launcher" || j.at("platform")!="windows" || j.at("architecture")!="x64")
        throw std::runtime_error("El canal del launcher no corresponde a Windows x64.");
    LauncherRelease r;r.version=j.at("version").get<std::string>();parse_update_version(r.version);
    r.url=j.at("url").get<std::string>();validate_update_url(r.url);
    if(r.url.size()<4 || r.url.substr(r.url.size()-4)!=".exe") throw std::runtime_error("La actualización del launcher debe ser un ejecutable.");
    r.sha256=j.at("sha256").get<std::string>();
    if(!valid_launcher_hash(r.sha256)) throw std::runtime_error("El launcher no tiene una comprobación SHA-256 válida.");
    if(!j.at("size").is_number_integer()) throw std::runtime_error("El tamaño del launcher no es válido.");
    auto size=j.at("size").get<std::int64_t>();
    if(size<=0 || size>64*1024*1024) throw std::runtime_error("El tamaño del launcher no es válido.");
    r.size=static_cast<std::uint64_t>(size);r.notes=j.value("notes",std::string{});return r;
}
inline std::wstring launcher_argument(const std::wstring& value) {
    std::wstring out=L"\"";unsigned slashes=0;
    for(wchar_t c:value) {
        if(c==L'\\') {++slashes;continue;}
        if(c==L'\"') {out.append(slashes*2+1,L'\\');out+=c;slashes=0;continue;}
        out.append(slashes,L'\\');slashes=0;out+=c;
    }
    out.append(slashes*2,L'\\');out+=L'\"';return out;
}
}
