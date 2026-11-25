/* Generated exact-build metadata; contains no game implementation. */
#define REGISTRY_OFFSET 0x141d9488UL
#define INIT_OFFSET 0x100fe598UL
#define HUD_CTOR_OFFSET 0x99c0fccUL
#define BUILD_NOTE_OFFSET 0x143200a8UL
#define STRING_CTOR_OFFSET 0x135d4c30UL
#define FACTORY_OFFSET 0x10101cc0UL
#define GET_BY_NAME_OFFSET 0x1010268cUL
#define GET_BY_ID_OFFSET 0x101026b4UL
#define ALLOCATE_OFFSET 0x135d4460UL
#define RELEASE_OFFSET 0x135d4320UL
#define RECT_CTOR_OFFSET 0x11eee468UL
#define MODULE_VERSION "0.1.1"
#define ENGINE_BUILD_ID "e96ddd68541814923c35460f8b99f27a66c51232"
static const unsigned char expected_build_id[20] = {0xe9,0x6d,0xdd,0x68,0x54,0x18,0x14,0x92,0x3c,0x35,0x46,0x0f,0x8b,0x99,0xf2,0x7a,0x66,0xc5,0x12,0x32};
struct WordPatch { unsigned long offset; unsigned int before, after; };
static const struct WordPatch word_patches[] = {
  {0x101026b4UL,0x7100981fU,0x71009c1fU}, /* effect ID unsigned upper bound */
  {0x101026e4UL,0x7100981fU,0x71009c1fU}, /* effect ID unsigned upper bound */
  {0x10103360UL,0x7100983fU,0x71009c3fU}, /* effect ID unsigned upper bound */
  {0x101033d8UL,0x7100983fU,0x71009c3fU}, /* effect ID unsigned upper bound */
  {0x10103450UL,0x7100983fU,0x71009c3fU}, /* effect ID unsigned upper bound */
  {0x101034ccUL,0x7100983fU,0x71009c3fU}, /* effect ID unsigned upper bound */
  {0x101035d4UL,0x7100995fU,0x71009d5fU}, /* effect ID unsigned upper bound */
  {0x10103bf0UL,0x7100993fU,0x71009d3fU}, /* effect ID unsigned upper bound */
  {0x10104058UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x10104090UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101040c0UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101040fcUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x1010417cUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101041acUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101041d8UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x1010420cUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x1010424cUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x1010427cUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101042e0UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101046f4UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101053a8UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x1010573cUL,0x7100993fU,0x71009d3fU}, /* effect ID unsigned upper bound */
  {0x101058a4UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0xb7d7ad4UL,0x7100981fU,0x71009c1fU}, /* effect ID unsigned upper bound */
  {0x106d18b8UL,0x7100981fU,0x71009c1fU}, /* effect ID unsigned upper bound */
  {0x106d3d58UL,0x7100985fU,0x71009c5fU}, /* effect ID unsigned upper bound */
  {0x101025e4UL,0xf104e27fU,0xf105027fU}, /* clear expanded table */
  {0xe1c18dcUL,0x71009edfU,0x7100a2dfU}, /* effect enumeration */
  {0xf38eeecUL,0x71009ebfU,0x7100a2bfU}, /* effect enumeration */
};
struct TablePatch { unsigned long offset; unsigned int before; };
static const struct TablePatch table_patches[] = {
  {0x100fe69cUL,0xf00206d9U},
  {0x10101a4cUL,0x900206c9U},
  {0x10101bf8UL,0x900206c9U},
  {0x10101da4UL,0x900206c9U},
  {0x10101f5cUL,0x900206c9U},
  {0x101021a8UL,0xf00206a9U},
  {0x10102350UL,0xf00206a9U},
  {0x101024f8UL,0xf00206a9U},
  {0x101025d4UL,0xf00206b4U},
  {0x101026c4UL,0xf00206a8U},
  {0x101026d4UL,0xf00206a8U},
  {0x101033b8UL,0xd00206a8U},
  {0x10103430UL,0xd00206a8U},
  {0x101034acUL,0xd00206a8U},
  {0x1010352cUL,0xd00206a8U},
  {0x10103630UL,0xd00206acU},
  {0x10103c58UL,0xd00206abU},
  {0x10104060UL,0xb00206a9U},
  {0x10104098UL,0xb00206a9U},
  {0x101040c8UL,0xb00206a9U},
  {0x10104104UL,0xb00206a9U},
  {0x10104184UL,0xb00206a9U},
  {0x101041b4UL,0xb00206a9U},
  {0x101041e0UL,0xb00206a9U},
  {0x10104214UL,0xb00206a9U},
  {0x10104254UL,0xb00206a9U},
  {0x10104284UL,0xb00206a9U},
  {0x101042e8UL,0xb00206a9U},
  {0x101046fcUL,0xb00206a9U},
  {0x10104facUL,0xb00206b7U},
  {0x10105214UL,0x900206a8U},
  {0x1010570cUL,0x900206b8U},
  {0x101058acUL,0x900206a9U},
};
struct FunctionWord { unsigned long offset; unsigned int expected; };
static const struct FunctionWord function_words[] = {
  {0x100fe598UL,0xa9ba7bfdU},
  {0x100fe59cUL,0xa9016ffcU},
  {0x100fe5a0UL,0xa90267faU},
  {0x100fe5a4UL,0xa9035ff8U},
  {0x10101cc0UL,0xd10243ffU},
  {0x10101cc4UL,0xa9047bfdU},
  {0x10101cc8UL,0xa90567faU},
  {0x10101cccUL,0xa9065ff8U},
  {0x135d4c30UL,0xb0004ed0U},
  {0x135d4c34UL,0xf9439611U},
  {0x135d4c38UL,0x911ca210U},
  {0x135d4c3cUL,0xd61f0220U},
  {0x1010268cUL,0xa9bf7bfdU},
  {0x10102690UL,0x910003fdU},
  {0x10102694UL,0xaa0003e1U},
  {0x10102698UL,0xf00206a0U},
  {0x99c0fccUL,0xa9bd7bfdU},
  {0x99c0fd0UL,0xf9000bf5U},
  {0x99c0fd4UL,0xa9024ff4U},
  {0x99c0fd8UL,0x910003fdU},
  {0x135d4460UL,0xb0004ed0U},
  {0x135d4464UL,0xf941a211U},
  {0x135d4468UL,0x910d0210U},
  {0x135d446cUL,0xd61f0220U},
  {0x135d4320UL,0xb0004ed0U},
  {0x135d4324UL,0xf9415211U},
  {0x135d4328UL,0x910a8210U},
  {0x135d432cUL,0xd61f0220U},
  {0x11eee468UL,0x1e2203e0U},
  {0x11eee46cUL,0x1e2203e1U},
  {0x11eee470UL,0x1e2203e2U},
  {0x11eee474UL,0x1e2203e3U},
};
