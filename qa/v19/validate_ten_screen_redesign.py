#!/usr/bin/env python3
"""V19 visual-only regression gate for the ten MANGA VIRUS screen areas."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DEV = ROOT / "app/src/neonReference/res"
A = "{http://schemas.android.com/apk/res/android}"


def check(condition, label):
    if not condition:
        raise AssertionError(label)
    print("PASS:", label)


def parse(path):
    return ET.parse(path).getroot()


palette = parse(DEV / "values/neon_v19_colors.xml")
colors = {node.attrib.get("name") for node in palette if node.tag == "color"}
required_colors = {
    "v19_bg", "v19_bg_top", "v19_surface", "v19_surface_raised", "v19_surface_alt",
    "v19_text", "v19_muted", "v19_cyan", "v19_blue", "v19_purple", "v19_pink",
    "v19_border", "v19_border_soft", "v19_cyan_soft", "v19_pink_soft", "v19_track",
    "v19_success", "v19_warning",
}
check(required_colors <= colors, "V19 shared palette is complete")

drawables = {path.stem for path in (DEV / "drawable").glob("v19_*.xml")}
required_drawables = {
    "v19_screen_background", "v19_header_background", "v19_surface_card",
    "v19_surface_card_selected", "v19_surface_card_ripple", "v19_action_dock",
    "v19_reader_panel", "v19_chip", "v19_bottom_navigation",
}
check(required_drawables <= drawables, "V19 shared drawable system is complete")

# Every explicit V19 color/drawable reference used by the screen overlays must resolve.
used_v19_colors = set()
used_v19_drawables = set()
for path in list((DEV / "layout").glob("*.xml")) + [ROOT / "app/src/main/res/layout/home_controller.xml"]:
    for kind, name in re.findall(r"@(color|drawable)/(v19_[A-Za-z0-9_]+)", path.read_text()):
        (used_v19_colors if kind == "color" else used_v19_drawables).add(name)
check(used_v19_colors <= colors, "all referenced V19 colors resolve")
check(used_v19_drawables <= drawables | {"v19_icon_home"}, "all referenced V19 drawables resolve")
check((ROOT / "app/src/main/res/drawable/v19_icon_home.xml").is_file(), "Home icon is available in the standard source set")

xml_files = list(DEV.rglob("*.xml"))
for path in xml_files:
    parse(path)
check(bool(xml_files), f"all {len(xml_files)} development XML resources parse")

# The ten screen areas are checked through their real existing binding targets.
targets = {
    "main_activity.xml": {"main_content", "controller_container", "app_bar", "bottom_nav"},
    "home_controller.xml": {"home_compose"},
    "library_controller.xml": {"library_layout", "library_grid_recycler", "library_top_actions", "filter_bottom_sheet"},
    "recents_controller.xml": {"recents_layout", "recycler", "download_bottom_sheet"},
    "browse_controller.xml": {"browse_layout", "source_recycler", "bottom_sheet"},
    "browse_source_controller.xml": {"source_layout", "catalogue_view", "floating_browse_bar", "popular_group", "latest_group", "filter_group"},
    "source_item.xml": {"source_card", "source_image", "title", "source_latest", "source_pin"},
    "source_global_search_controller.xml": {"recycler", "empty_view"},
    "source_global_search_controller_card.xml": {"title_wrapper", "title", "subtitle", "title_more_icon", "source_card", "progress", "recycler"},
    "source_global_search_controller_card_item.xml": {"maskable_layout", "itemImage", "progress", "gradient", "favorite_button", "duplicate_in_library_button", "title"},
    "source_global_search_filter_header.xml": {"filter_button_group", "pinned_button", "has_results_button"},
    "manga_grid_item.xml": {"card", "cover_thumbnail", "title", "subtitle"},
    "manga_list_item.xml": {"list_card", "cover_thumbnail", "title", "subtitle"},
    "manga_header_item.xml": {"manga_header_layout", "manga_cover", "title", "favorite_button", "start_reading_button", "chapters_title"},
    "chapters_item.xml": {"chapter_layout", "chapter_card", "chapter_title", "download_button"},
    "reader_activity.xml": {"reader_layout", "viewer_container", "page_number", "app_bar", "chapters_sheet"},
    "download_item.xml": {"front_view", "title", "chapter_title", "download_progress"},
    "recent_manga_item.xml": {"recent_manga_layout", "recent_card", "cover_thumbnail", "title"},
    "settings_search_controller.xml": {"recycler", "progress"},
}
for filename, expected in targets.items():
    path = DEV / "layout" / filename
    if filename == "home_controller.xml":
        path = ROOT / "app/src/main/res/layout" / filename
    check(path.is_file(), f"{filename} layout exists")
    ids = {node.attrib[A + "id"].split("/")[-1] for node in parse(path).iter() if A + "id" in node.attrib}
    check(expected <= ids, f"{filename} retains its real view binding IDs")

# Check the new source heading keeps the original adapter binding contract.
source_heading = parse(DEV / "layout/source_header_item.xml")
heading_ids = [node.attrib[A + "id"] for node in source_heading.iter() if A + "id" in node.attrib]
check(heading_ids == ["@+id/title"], "source header keeps the single dynamic title ID")
check("android:text=\"" not in (DEV / "layout/source_header_item.xml").read_text(),
      "source header does not hard-code a source name")

# The download panel is a visual projection of the real queue and uses real manga covers.
download_panel = (ROOT / "app/src/main/java/eu/kanade/tachiyomi/ui/download/NeonDownloadPanel.kt").read_text()
for token in ("visibleRows", "Download.State.DOWNLOADING", "Download.State.QUEUE", "Download.State.DOWNLOADED",
              "val completedCount = rows.count", "في الانتظار", "متعثر", "loadManga(row.download.manga)",
              "onPause", "onResume", "onDelete", "onPrioritize"):
    check(token in download_panel, f"download panel retains live queue wiring: {token}")
check("Solo Leveling" not in download_panel and "Sample manga" not in download_panel,
      "download panel contains no mock manga records")

home_controller = (ROOT / "app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeController.kt").read_text()
check("getFavoriteMangas().executeOnIO()" in home_controller, "home dashboard reads real favorite manga")
check('getRecentMangaLimit("", 0, false)' in home_controller, "home dashboard reads real reading history")
check("MangaDetailsController(manga)" in home_controller, "home cards open the existing manga details controller")
check("getCatalogueSources().size" in home_controller, "home source count comes from the installed source manager")
check("Sample manga" not in home_controller and "Solo Leveling" not in home_controller, "home dashboard does not seed mock titles")
check('name="nav_home" type="id"' in (ROOT / "app/src/main/res/values/ids.xml").read_text(), "home navigation ID is available to all variants")
check("@+id/nav_home" in (DEV / "menu/bottom_navigation.xml").read_text() and "@+id/nav_settings" in (DEV / "menu/bottom_navigation.xml").read_text(), "dev bottom navigation includes Home and Settings")
check("@+id/nav_home" in (ROOT / "app/src/main/res/menu/bottom_navigation.xml").read_text(), "standard bottom navigation includes Home")
check("@+id/nav_home" in (ROOT / "app/src/main/res/menu/side_navigation.xml").read_text(), "standard side navigation includes Home")
check("in -4..-1" in (ROOT / "app/src/main/java/eu/kanade/tachiyomi/ui/setting/SettingsGeneralController.kt").read_text(), "starting-screen preference includes the Home option")
main_activity = (ROOT / "app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt").read_text()
check("R.id.nav_home -> HomeController()" in main_activity, "MainActivity routes Home to HomeController")
check("is HomeController -> R.id.nav_home" in main_activity, "MainActivity restores Home tab selection")
check("-4 -> R.id.nav_home" in main_activity, "Home is supported as a fixed start screen")
for values_file in (ROOT / "app/src/main/res/values/strings.xml", ROOT / "app/src/main/res/values-ar/strings.xml"):
    strings_root = parse(values_file)
    check(any(node.attrib.get("name") == "home" for node in strings_root.findall("string")), f"localized Home label exists: {values_file.parent.name}")

settings_styler = (ROOT / "app/src/main/java/eu/kanade/tachiyomi/ui/setting/NeonSettingsStyler.kt").read_text()
check("PreferenceGroupAdapter" in settings_styler and "preferenceAt" in settings_styler,
      "settings styling continues to use the original Preference adapter")
check("setOnClickListener" not in settings_styler,
      "settings styling does not replace preference click behavior")

# Confirm app identity and the already-required FlexibleAdapter fallback remain intact.
gradle = (ROOT / "app/build.gradle.kts").read_text()
check('applicationIdSuffix = ".enhanced"' in gradle, "dev application ID suffix remains unchanged")
check(("versionCode = AndroidVersions.versionCode + 3" in gradle) if "-enhanced-v21-dev" in gradle else (("versionCode = AndroidVersions.versionCode + 2" in gradle) if "-enhanced-v20-dev" in gradle else ("versionCode = AndroidVersions.versionCode + 1" in gradle)), "dev version code is incremented for the current redesign")
root_gradle = (ROOT / "build.gradle.kts").read_text()
check("GoogleMavenCentralFlexibleAdapterMirror" in root_gradle and "MavenCentralRepo1FlexibleAdapterFallback" in root_gradle,
      "FlexibleAdapter Maven fallback mirrors are retained")

workflow = (ROOT / ".github/workflows/quality_gate.yml").read_text()
check("qa/v19/validate_ten_screen_redesign.py" in workflow, "V19 screen regression gate is part of GitHub Actions")

print("V19 TEN-SCREEN VISUAL STATIC CHECKS PASSED — NOT AN APK BUILD OR DEVICE TEST")
