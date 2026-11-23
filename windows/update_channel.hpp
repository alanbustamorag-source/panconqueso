#pragma once
#include "vendor/nlohmann/json.hpp"
#include <array>
#include <algorithm>
#include <charconv>
#include <cctype>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <limits>

namespace bodrio {
inline constexpr const char* update_url = "https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/main/latest.json";
inline constexpr const char* behavior_uuid = "0c6db7ac-4c6f-4942-bae6-c16b5065036b";
inline constexpr const char* resource_uuid = "1d763fb9-7cb5-429f-9789-b4b16e2301d1";
using PackVersion = std::array<unsigned,3>;
struct UpdateChannel {
    std::string version, url, sha256, notes;
    std::uint64_t size=0;
    PackVersion behavior{}, resource{};
};
inline PackVersion parse_update_version(const std::string& text) {
    PackVersion result{};
    size_t start=0;
    for(size_t i=0;i<3;i++) {
        auto end=text.find('.',start);
        if(i==2) end=text.size();
        else if(end==std::string::npos) throw std::runtime_error("La versión del canal no es válida.");
        if(end==start) throw std::runtime_error("La versión del canal no es válida.");
        auto conversion=std::from_chars(text.data()+start,text.data()+end,result[i]);
        if(conversion.ec!=std::errc{} || conversion.ptr!=text.data()+end || result[i]>2147483647u)
            throw std::runtime_error("La versión del canal no es válida.");
        start=end+1;
    }
    return result;
}
inline PackVersion parse_pack_version(const nlohmann::json& array) {
    if(!array.is_array() || array.size()!=3) throw std::runtime_error("La versión del pack no es válida.");
    PackVersion out{};
    for(size_t i=0;i<3;i++) {
        if(!array[i].is_number_integer()) throw std::runtime_error("La versión del pack no es válida.");
        auto n=array[i].get<std::int64_t>();
        if(n<0 || n>2147483647) throw std::runtime_error("La versión del pack no es válida.");
        out[i]=static_cast<unsigned>(n);
    }
    return out;
}
inline void validate_update_url(const std::string& url) {
    const std::string prefix="https://raw.githubusercontent.com/alanbustamorag-source/panconqueso/";
    if(url.compare(0,prefix.size(),prefix)!=0 || url.find("/../")!=std::string::npos || url.find("/./")!=std::string::npos)
        throw std::runtime_error("La descarga no pertenece al canal de Bodriosafío.");
    for(size_t i=prefix.size();i<url.size();i++) {
        unsigned char c=static_cast<unsigned char>(url[i]);
        if(!std::isalnum(c) && c!='/' && c!='.' && c!='_' && c!='-')
            throw std::runtime_error("La URL del canal no es válida.");
    }
}
inline UpdateChannel parse_update_channel(const std::string& text,const std::string& platform="windows",const std::string& launcher="0.1.4") {
    if(text.empty() || text.size()>128*1024) throw std::runtime_error("El canal de actualizaciones tiene un tamaño no válido.");
    auto j=nlohmann::json::parse(text);
    if(j.at("schema")!=1 || j.at("project")!="bodriosafio" || j.at("channel")!="stable")
        throw std::runtime_error("El canal de actualizaciones no es compatible.");
    if(j.at("minecraft")!="26.60" && j.at("minecraft")!="26.62" && j.at("minecraft")!="26.63")
        throw std::runtime_error("La actualización requiere otra versión de Minecraft.");
    if(parse_update_version(launcher)<parse_update_version(j.at("minimum_launcher").at(platform).get<std::string>()))
        throw std::runtime_error("Actualiza primero el launcher para usar este addon.");
    UpdateChannel out;
    out.version=j.at("version").get<std::string>(); parse_update_version(out.version);
    auto a=j.at("addon");
    out.url=a.at("url").get<std::string>(); validate_update_url(out.url);
    out.sha256=a.at("sha256").get<std::string>();
    if(out.sha256.size()!=64) throw std::runtime_error("La actualización no tiene una comprobación válida.");
    for(char& c:out.sha256) {
        c=static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
        if(!((c>='0'&&c<='9')||(c>='a'&&c<='f'))) throw std::runtime_error("La comprobación de descarga no es válida.");
    }
    if(!a.at("size").is_number_integer()) throw std::runtime_error("El tamaño de la actualización no es válido.");
    auto size=a.at("size").get<std::int64_t>();
    if(size<=0 || size>64*1024*1024) throw std::runtime_error("El tamaño de la actualización no es válido.");
    out.size=static_cast<std::uint64_t>(size);
    out.notes=j.value("notes",std::string{});
    auto packs=j.at("packs"); bool bp=false,rp=false;
    if(!packs.is_array() || packs.size()!=2) throw std::runtime_error("El canal debe identificar ambos packs.");
    for(const auto& p:packs) {
        if(p.at("uuid")==behavior_uuid && p.at("type")=="data" && !bp) {out.behavior=parse_pack_version(p.at("version"));bp=true;}
        else if(p.at("uuid")==resource_uuid && p.at("type")=="resources" && !rp) {out.resource=parse_pack_version(p.at("version"));rp=true;}
        else throw std::runtime_error("El canal no identifica los packs de Bodriosafío.");
    }
    if(!bp || !rp || out.behavior!=parse_update_version(out.version)) throw std::runtime_error("Las versiones del canal no coinciden.");
    return out;
}
}

