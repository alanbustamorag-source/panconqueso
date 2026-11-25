/* Bodriosafio native effect registration. Android ARM64, exact 26.60.4 build.
 * No Minecraft executable code or assets are included in this module.
 * Registry operations use the game's own allocator, constructor and name map.
 */
typedef unsigned long usize;
typedef unsigned long uptr;
typedef unsigned int u32;
typedef unsigned short u16;
typedef unsigned char u8;
typedef long s64;
extern int dl_iterate_phdr(int (*)(void *, usize, void *), void *);
extern void *dlopen(const char *, int);
extern void *dlsym(void *, const char *);
extern int getpagesize(void);
extern void *mmap(void *, usize, int, int, int, s64);
extern int munmap(void *, usize);
extern int mprotect(void *, usize, int);
extern int memcmp(const void *, const void *, usize);
extern void *memcpy(void *, const void *, usize);
extern void *memset(void *, int, usize);
extern usize strlen(const char *);
extern char *getenv(const char *);
extern int snprintf(char *, usize, const char *, ...);
extern int __android_log_write(int, const char *, const char *);
extern int open(const char *, int, ...);
extern long write(int, const void *, usize);
extern int close(int);
struct DlInfo { const char *name; void *base; const char *symbol; void *symbol_addr; };
extern int dladdr(const void *, struct DlInfo *);
struct Phdr { u32 type, flags; uptr offset, vaddr, paddr, filesz, memsz, align; };
struct PhdrInfo { uptr base; const char *name; const struct Phdr *phdr; u16 phnum; };
#include "profile.h"

#define API __attribute__((visibility("default")))
#define COUNT(a) (sizeof(a) / sizeof((a)[0]))
#define REGISTRY_OFFSET 0x141d8908UL
#define INIT_OFFSET 0x100fd964UL
#define HUD_CTOR_OFFSET 0x99c0e2cUL
#define BUILD_NOTE_OFFSET 0x143200a8UL
#define EFFECT_ID 39U
#define OLD_COUNT 39U
#define NEW_COUNT 40U

static uptr game_base;
static int build_note_readable;
static uptr registry_page;
static void **new_registry;
static usize page_size;
static volatile int installed;
static volatile int state;
static char preload_path[2048];
static char status_path[2048];
static void (*original_init)(const void *, const void *);
static void (*original_hud_ctor)(void *, void *);
static int (*install_hook)(void *, void *, void **, int);
static u8 (*remove_hook)(void *, void *);
static void *effect;
static const char identifier[] = "bodriosafio:anticombustion";

static void status(const char *phase, const char *detail) {
  char data[1024];
  int len = snprintf(data, sizeof data,
    "{\"module\":\"bodriosafio-native-effects\",\"version\":\"0.1.0\","
    "\"phase\":\"%s\",\"detail\":\"%s\",\"effect_id\":39,"
    "\"identifier\":\"bodriosafio:anticombustion\",\"vanilla_count\":39,"
    "\"registry_count\":40,\"game_build_id\":\"eb291b883dfdfaff70c4999c2eabd61885ae3e15\"}\n",
    phase, detail);
  __android_log_write(phase[0] == 'e' ? 6 : 4, "BodriosafioEffects", data);
  if (status_path[0] && len > 0 && (usize)len < sizeof data) {
    int fd = open(status_path, 1 | 64 | 512, 0600);
    if (fd >= 0) { (void)write(fd, data, (usize)len); close(fd); }
  }
}

static int ends(const char *s, const char *suffix) {
  if (!s) return 0;
  usize a = strlen(s), b = strlen(suffix);
  return a >= b && !memcmp(s + a - b, suffix, b);
}

static int find_modules(void *raw, usize size, void *unused) {
  (void)size; (void)unused;
  const struct PhdrInfo *info = raw;
  if (ends(info->name, "/libminecraftpe.so") || ends(info->name, "libminecraftpe.so")) {
    game_base = info->base;
    for (u16 i=0; i<info->phnum; ++i) {
      const struct Phdr *p = &info->phdr[i];
      if (p->type == 1 && (p->flags & 4) && BUILD_NOTE_OFFSET >= p->vaddr
          && BUILD_NOTE_OFFSET + 20 <= p->vaddr + p->filesz) build_note_readable = 1;
    }
  }
  if (ends(info->name, "/libpreloader.so") || ends(info->name, "libpreloader.so"))
    snprintf(preload_path, sizeof preload_path, "%s", info->name);
  return 0;
}

static void resolve_status_path(void) {
  const char *configured = getenv("BODRIOSAFIO_EFFECTS_STATUS");
  if (configured && configured[0] == '/' && strlen(configured) < sizeof status_path) {
    memcpy(status_path, configured, strlen(configured) + 1);
    return;
  }
  struct DlInfo info = {0};
  if (!dladdr((const void *)&resolve_status_path, &info) || !info.name) return;
  usize n = strlen(info.name);
  if (n + 32 >= sizeof status_path) return;
  while (n && info.name[n - 1] != '/') --n;
  if (!n) return;
  memcpy(status_path, info.name, n);
  memcpy(status_path + n, "native-effects-status.json", 27);
}

// ARM64 ADRP uses 4 KiB addressing pages even on an Android 16 KiB-page kernel.
static int adrp_word(uptr pc, uptr target_page, u32 before, u32 *out) {
  s64 diff = (s64)(target_page & ~4095UL) - (s64)(pc & ~4095UL);
  if ((diff & 4095) || diff < -4294967296L || diff > 4294963200L) return 0;
  uptr imm = ((uptr)(diff >> 12)) & 0x1fffff;
  *out = (before & 0x9f00001fU) | ((u32)(imm & 3) << 29) | ((u32)(imm >> 2) << 5);
  return 1;
}

static int put_word(uptr at, u32 word) {
  uptr page = at & ~(page_size - 1);
  if (mprotect((void *)page, page_size, 1 | 2 | 4)) return 0;
  *(volatile u32 *)at = word;
  // Equivalent cache maintenance to the AArch64 compiler-runtime primitive;
  // avoid relying on a non-public __clear_cache symbol in Android's libc.
  uptr ctr;
  __asm__ volatile("mrs %0, ctr_el0" : "=r"(ctr));
  if (!(ctr & (1UL << 28))) {
    uptr line = 4UL << ((ctr >> 16) & 15);
    for (uptr p=at & ~(line-1); p<at+4; p+=line) __asm__ volatile("dc cvau, %0" :: "r"(p) : "memory");
  }
  __asm__ volatile("dsb ish" ::: "memory");
  if (!(ctr & (1UL << 29))) {
    uptr line = 4UL << (ctr & 15);
    for (uptr p=at & ~(line-1); p<at+4; p+=line) __asm__ volatile("ic ivau, %0" :: "r"(p) : "memory");
    __asm__ volatile("dsb ish" ::: "memory");
  }
  __asm__ volatile("isb" ::: "memory");
  if (mprotect((void *)page, page_size, 1 | 4)) return 0;
  return 1;
}

static int allocate_registry(void) {
  for (unsigned i = 0; i < 64; ++i) {
    void *hint = i ? (void *)((game_base & ~(page_size - 1)) + 0x18000000UL + (uptr)i * 0x1000000UL) : (void *)0;
    void *mem = mmap(hint, page_size, 1 | 2, 2 | 32, -1, 0);
    if (mem == (void *)-1) continue;
    u32 encoded;
    int valid = 1;
    for (usize j = 0; j < COUNT(table_patches); ++j)
      if (!adrp_word(game_base + table_patches[j].offset, (uptr)mem,
                     table_patches[j].before, &encoded)) { valid = 0; break; }
    if (valid) {
      registry_page = (uptr)mem;
      new_registry = (void **)(registry_page + (REGISTRY_OFFSET & 4095));
      return 1;
    }
    munmap(mem, page_size);
  }
  return 0;
}

static void register_effect(void) {
  typedef void (*StringCtor)(void *, const char *, usize);
  typedef void *(*Factory)(u32, const void *, const char *, const u8 *, const int *, const int *);
  typedef void *(*Lookup)(const void *);
  typedef void *(*ById)(u32);
  StringCtor string_ctor = (StringCtor)(game_base + 0x135d40c0UL);
  Factory factory = (Factory)(game_base + 0x1010108cUL);
  Lookup lookup = (Lookup)(game_base + 0x10101a58UL);
  ById by_id = (ById)(game_base + 0x10101a80UL);
  // A genuine libc++ string, constructed by this exact engine. It remains
  // alive for the process; the factory copies it into its own native objects.
  static u8 native_name[24] __attribute__((aligned(8)));
  static int name_ready;
  if (!name_ready) { string_ctor(native_name, identifier, sizeof identifier - 1); name_ready = 1; }
  if (new_registry[EFFECT_ID] && lookup(native_name) != new_registry[EFFECT_ID]) {
    state = -1; status("error", "native ID collision; registration refused"); return;
  }
  u8 harmful = 0;
  int color = 0xff9166, icon = 0;
  effect = factory(EFFECT_ID, native_name, "effect.bodriosafio.anticombustion", &harmful, &color, &icon);
  if (!effect || *(u32 *)((u8 *)effect + 8) != EFFECT_ID || new_registry[EFFECT_ID] != effect
      || lookup(native_name) != effect || by_id(EFFECT_ID) != effect) {
    state = -1; status("error", "native name and ID verification failed"); return;
  }
  // The generic factory leaves mIconName as an empty, inline string. The
  // engine's own constructor initializes it to the resource-pack icon stem.
  string_ctor((u8 *)effect + 0xb0, "anticombustion", 14);
  state = 2;
  status("registered", "native effect created; name and ID resolve to the same object");
}

static void init_effects_hook(const void *version, const void *experiments) {
  original_init(version, experiments);
  register_effect();
}

static void hud_ctor_hook(void *self, void *client) {
  original_hud_ctor(self, client);
  // The engine constructor unrolls exactly 39 HUD entries (36 bytes each).
  // Actor::addEffect grows its effect vector dynamically, but the HUD does
  // not. Extend its vector with the same allocator and Rect constructors.
  void **vector = (void **)((u8 *)self + 0x18);
  usize length = (uptr)vector[1] - (uptr)vector[0];
  if (length != OLD_COUNT * 36) return;
  typedef void *(*Allocate)(usize);
  typedef void (*Delete)(void *);
  typedef void (*RectCtor)(void *);
  Allocate allocate = (Allocate)(game_base + 0xe803540UL);
  Delete release = (Delete)(game_base + 0xe803424UL);
  RectCtor rect = (RectCtor)(game_base + 0xe8305e8UL);
  u8 *expanded = allocate(NEW_COUNT * 36);
  memcpy(expanded, vector[0], length);
  memset(expanded + length, 0, 36);
  rect(expanded + length + 4);
  rect(expanded + length + 0x14);
  void *old = vector[0];
  vector[0] = expanded;
  vector[1] = vector[2] = expanded + NEW_COUNT * 36;
  release(old);
}

static int bootstrap(void) {
  if (installed) return installed > 0;
  resolve_status_path();
  dl_iterate_phdr(find_modules, 0);
  if (!game_base) { status("error", "Minecraft engine was not loaded before the module"); return 0; }
  if (!build_note_readable || memcmp((void *)(game_base + BUILD_NOTE_OFFSET), expected_build_id, 20)) {
    status("error", "unsupported Minecraft build; no patches applied"); return 0;
  }
  if (!preload_path[0]) { status("error", "Levi preloader was not found"); return 0; }
  void *handle = dlopen(preload_path, 2 | 4);
  if (!handle) { status("error", "could not access the loaded preloader"); return 0; }
  install_hook = (int (*)(void *,void *,void **,int))dlsym(handle, "_ZN2pl6memory4hookEPvS1_PS1_NS0_12HookPriorityE");
  remove_hook = (u8 (*)(void *,void *))dlsym(handle, "_ZN2pl6memory6unhookEPvS1_");
  if (!install_hook || !remove_hook) { status("error", "preloader hook API unavailable"); return 0; }
  page_size = (usize)getpagesize();
  if ((page_size != 4096 && page_size != 16384) || (REGISTRY_OFFSET & 4095) + NEW_COUNT * 8 > page_size) {
    status("error", "unsupported memory page size"); return 0;
  }
  for (usize i=0; i<COUNT(word_patches); ++i)
    if (*(u32 *)(game_base + word_patches[i].offset) != word_patches[i].before) {
      status("error", "range-check fingerprint mismatch; no patches applied"); return 0;
    }
  for (usize i=0; i<COUNT(table_patches); ++i)
    if (*(u32 *)(game_base + table_patches[i].offset) != table_patches[i].before) {
      status("error", "registry-reference fingerprint mismatch; no patches applied"); return 0;
    }
  void **old = (void **)(game_base + REGISTRY_OFFSET);
  for (usize i=0; i<OLD_COUNT; ++i)
    if (old[i]) { status("error", "registry already initialized; restart the launcher process"); return 0; }
  if (!allocate_registry()) { status("error", "could not allocate a reachable expanded registry"); return 0; }
  usize words=0, tables=0;
  int hud_hooked=0, init_hooked=0;
  for (; words<COUNT(word_patches); ++words)
    if (!put_word(game_base + word_patches[words].offset, word_patches[words].after)) goto rollback;
  for (; tables<COUNT(table_patches); ++tables) {
    u32 next;
    if (!adrp_word(game_base + table_patches[tables].offset, registry_page, table_patches[tables].before, &next)
      || !put_word(game_base + table_patches[tables].offset, next)) goto rollback;
  }
  if (install_hook((void *)(game_base+HUD_CTOR_OFFSET), (void *)&hud_ctor_hook,
                   (void **)&original_hud_ctor, 200) != 0) goto rollback;
  hud_hooked=1;
  if (!original_hud_ctor) goto rollback;
  if (install_hook((void *)(game_base+INIT_OFFSET), (void *)&init_effects_hook,
                   (void **)&original_init, 200) != 0) goto rollback;
  init_hooked=1;
  if (!original_init) goto rollback;
  installed = 1; state = 1;
  status("waiting_for_engine_init", "expanded registry installed; native initialization hook ready");
  return 1;
rollback:
  {
    if (init_hooked) remove_hook((void *)(game_base+INIT_OFFSET), (void *)&init_effects_hook);
    if (hud_hooked) remove_hook((void *)(game_base+HUD_CTOR_OFFSET), (void *)&hud_ctor_hook);
    int restored = 1;
    for (usize i=0; i<COUNT(table_patches); ++i)
      if (!put_word(game_base+table_patches[i].offset, table_patches[i].before)) restored=0;
    for (usize i=0; i<COUNT(word_patches); ++i)
      if (!put_word(game_base+word_patches[i].offset, word_patches[i].before)) restored=0;
    // Do not free memory still reachable by an instruction if OS permission
    // restoration itself failed. JNI failure prevents launching the game.
    if (restored) { munmap((void *)registry_page,page_size); registry_page=0; new_registry=0; }
  }
  installed=-1; state=-1;
  status("error", "native patch installation failed and was rolled back");
  return 0;
}

API int JNI_OnLoad(void *vm, void *reserved) {
  (void)vm; (void)reserved;
  return bootstrap() ? 0x00010006 : -1;
}
API void mod_init(void) { (void)bootstrap(); }
API int bodriosafio_effect_state(void) { return state; }
