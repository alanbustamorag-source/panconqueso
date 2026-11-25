/* Exact ARM64 engine profile. Generated from user-provided split. */
static const unsigned char expected_build_id[20] = {0xeb,0x29,0x1b,0x88,0x3d,0xfd,0xfa,0xff,0x70,0xc4,0x99,0x9c,0x2e,0xab,0xd6,0x18,0x85,0xae,0x3e,0x15};
struct WordPatch { unsigned long offset; unsigned int before, after; };
static const struct WordPatch word_patches[] = {
  {0x10101a80UL,0x7100981fU,0x71009c1fU}, /* effect ID unsigned upper bound */
  {0x10101ab0UL,0x7100981fU,0x71009c1fU}, /* effect ID unsigned upper bound */
  {0x1010272cUL,0x7100983fU,0x71009c3fU}, /* effect ID unsigned upper bound */
  {0x101027a4UL,0x7100983fU,0x71009c3fU}, /* effect ID unsigned upper bound */
  {0x1010281cUL,0x7100983fU,0x71009c3fU}, /* effect ID unsigned upper bound */
  {0x10102898UL,0x7100983fU,0x71009c3fU}, /* effect ID unsigned upper bound */
  {0x101029a0UL,0x7100995fU,0x71009d5fU}, /* effect ID unsigned upper bound */
  {0x10102fbcUL,0x7100993fU,0x71009d3fU}, /* effect ID unsigned upper bound */
  {0x10103424UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x1010345cUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x1010348cUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101034c8UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x10103548UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x10103578UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101035a4UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101035d8UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x10103618UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x10103648UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x101036acUL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x10103ac0UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x10104774UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0x10104b08UL,0x7100993fU,0x71009d3fU}, /* effect ID unsigned upper bound */
  {0x10104c70UL,0x7100991fU,0x71009d1fU}, /* effect ID unsigned upper bound */
  {0xb7d742cUL,0x7100981fU,0x71009c1fU}, /* effect ID unsigned upper bound */
  {0x106d0c84UL,0x7100981fU,0x71009c1fU}, /* effect ID unsigned upper bound */
  {0x106d3124UL,0x7100985fU,0x71009c5fU}, /* effect ID unsigned upper bound */
  {0x101019b0UL,0xf104e27fU,0xf105027fU}, /* clear expanded table */
  {0xe1c0cf0UL,0x71009edfU,0x7100a2dfU}, /* effect enumeration */
  {0xf38e2b8UL,0x71009ebfU,0x7100a2bfU}, /* effect enumeration */
};
struct TablePatch { unsigned long offset; unsigned int before; };
static const struct TablePatch table_patches[] = {
  {0x100fda68UL,0xf00206d9U},
  {0x10100e18UL,0x900206c9U},
  {0x10100fc4UL,0x900206c9U},
  {0x10101170UL,0xf00206a9U},
  {0x10101328UL,0xf00206a9U},
  {0x10101574UL,0xf00206a9U},
  {0x1010171cUL,0xf00206a9U},
  {0x101018c4UL,0xf00206a9U},
  {0x101019a0UL,0xf00206b4U},
  {0x10101a90UL,0xf00206a8U},
  {0x10101aa0UL,0xf00206a8U},
  {0x10102784UL,0xd00206a8U},
  {0x101027fcUL,0xd00206a8U},
  {0x10102878UL,0xd00206a8U},
  {0x101028f8UL,0xd00206a8U},
  {0x101029fcUL,0xd00206acU},
  {0x10103024UL,0xb00206abU},
  {0x1010342cUL,0xb00206a9U},
  {0x10103464UL,0xb00206a9U},
  {0x10103494UL,0xb00206a9U},
  {0x101034d0UL,0xb00206a9U},
  {0x10103550UL,0xb00206a9U},
  {0x10103580UL,0xb00206a9U},
  {0x101035acUL,0xb00206a9U},
  {0x101035e0UL,0xb00206a9U},
  {0x10103620UL,0xb00206a9U},
  {0x10103650UL,0xb00206a9U},
  {0x101036b4UL,0xb00206a9U},
  {0x10103ac8UL,0xb00206a9U},
  {0x10104378UL,0x900206b7U},
  {0x101045e0UL,0x900206a8U},
  {0x10104ad8UL,0x900206b8U},
  {0x10104c78UL,0x900206a9U},
};
