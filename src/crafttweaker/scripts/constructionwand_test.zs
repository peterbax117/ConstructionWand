// Sample pack-maker script for the -Pcrafttweaker test run: replaces the stone wand recipe with a cheaper one
// and removes the wand upgrade recipe. Checked by WandFeatureTests.craftTweakerScript*.
craftingTable.remove(<item:constructionwand:stone_wand>);
craftingTable.addShapeless("easy_stone_wand", <item:constructionwand:stone_wand>, [<item:minecraft:stick>, <item:minecraft:cobblestone>]);
craftingTable.removeByName("constructionwand:dynamic/wand_upgrade");
