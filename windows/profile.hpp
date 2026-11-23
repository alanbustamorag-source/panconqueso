#pragma once
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace bodrio {
constexpr const char* native_sha256 = "b6f1d16b1d455c6127b1c9f91b3b400c9a7c1c734c52381bc52387ca626afcf8";
#ifndef BODRIO_EFFECTS_SHA256
#define BODRIO_EFFECTS_SHA256 "24ca8d216db6a23acc5f6ec5ccd99735abb5eb1a05bc6ecc1d155fcf9335ea6a"
#endif
constexpr const char* effects_sha256 = BODRIO_EFFECTS_SHA256;
constexpr const char* addon_sha256 = "900687d53f42d257fcd0642cde8a49952aad696e42b99b467c747879f5e255e3";

inline bool supported_effects_version(unsigned a,unsigned b,unsigned c,unsigned d) {
    return a==1 && b==26 && c==60 && d==5;
}

inline bool supported_version(unsigned a, unsigned b, unsigned c, unsigned /*d*/) {
    // Hotfix support does not authorize loading native offsets from another build.
    const unsigned release = c >= 1000 ? c / 100 : c;
    return (a == 26 && (b == 60 || b == 62 || b == 63)) ||
           (a == 1 && b == 26 && (release == 60 || release == 62 || release == 63));
}

inline std::string game_display_version(unsigned a, unsigned b, unsigned c, unsigned d) {
    if (a >= 26) {
        auto result = std::to_string(a) + "." + std::to_string(b);
        if (c || d) result += "." + std::to_string(c);
        if (d) result += "." + std::to_string(d);
        return result;
    }
    const unsigned release = c >= 1000 ? c / 100 : c;
    const unsigned patch = c >= 1000 ? c % 100 : d;
    auto result = a == 1 && b >= 26 ? std::to_string(b) + "." + std::to_string(release)
                                  : std::to_string(a) + "." + std::to_string(b) + "." + std::to_string(release);
    if (patch) result += "." + std::to_string(patch);
    if (c >= 1000 && d) {
        if (!patch) result += ".0";
        result += "." + std::to_string(d);
    }
    return result;
}

inline std::string trim(const std::string& s) {
    auto a = s.find_first_not_of(" \t\r\n");
    if (a == std::string::npos) return {};
    auto b = s.find_last_not_of(" \t\r\n");
    return s.substr(a, b - a + 1);
}

inline std::string uncomment(std::string s) {
    auto p = s.find('#');
    if (p != std::string::npos) s.resize(p);
    return trim(s);
}

inline std::string configure_nether(std::string text) {
    if (text.size() > 1024 * 1024) throw std::runtime_error("Configuracion demasiado grande.");
    if (text.find('\0') != std::string::npos)
        throw std::runtime_error("El config.ini contiene datos binarios o UTF-16.");
    if (text.compare(0, 3, "\xef\xbb\xbf") == 0) text.erase(0, 3);
    if (text.empty()) {
        return "# Bodriosafio Launcher 0.1.1 - Minecraft 26.60\r\n"
               "[Overworld]\r\nmin=-64\r\nmax=320\r\n\r\n"
               "[Nether]\r\nmin=0\r\nmax=256\r\n\r\n"
               "[TheEnd]\r\nmin=0\r\nmax=256\r\n";
    }
    const std::string newline = text.find("\r\n") != std::string::npos ? "\r\n" : "\n";
    std::vector<std::string> lines;
    for (size_t start = 0; start < text.size();) {
        auto end = text.find('\n', start);
        std::string line = text.substr(start, end == std::string::npos ? std::string::npos : end - start);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        lines.push_back(line);
        if (end == std::string::npos) break;
        start = end + 1;
    }
    bool in_nether = false, found = false, got_min = false, got_max = false;
    std::vector<std::string> out;
    auto finish_section = [&] {
        if (in_nether) {
            if (!got_min) out.push_back("min=0");
            if (!got_max) out.push_back("max=256");
        }
    };
    for (const auto& line : lines) {
        auto clean = uncomment(line);
        if (!clean.empty() && clean.front() != '[' && clean.find('=') == std::string::npos)
            throw std::runtime_error("El config.ini contiene una linea que el modulo no reconoce.");
        if (!clean.empty() && clean.front() == '[') {
            auto close = clean.find(']');
            if (close == std::string::npos) throw std::runtime_error("Seccion INI incompleta.");
            finish_section();
            in_nether = clean.substr(1, close - 1) == "Nether";
            got_min = got_max = false;
            found = found || in_nether;
        }
        bool replaced = false;
        auto eq = clean.find('=');
        if (in_nether && eq != std::string::npos) {
            auto key = trim(clean.substr(0, eq));
            if (key == "min" || key == "max") {
                auto comment = line.find('#');
                std::string updated = key == "min" ? "min=0" : "max=256";
                if (comment != std::string::npos) updated += " " + line.substr(comment);
                out.push_back(updated);
                if (key == "min") got_min = true; else got_max = true;
                replaced = true;
            }
        }
        if (!replaced) out.push_back(line);
    }
    finish_section();
    if (!found) {
        if (!out.empty() && !out.back().empty()) out.push_back("");
        out.insert(out.end(), {"[Nether]", "min=0", "max=256"});
    }
    std::string result;
    for (const auto& line : out) result += line + newline;
    return result;
}
} // namespace bodrio
