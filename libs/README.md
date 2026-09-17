# 本地构建依赖

使用 Java 17，将以下依赖放入本目录并保留对应文件名，然后运行 `gradlew.bat build`。本目录的 JAR 被 `.gitignore` 排除。

- `epicfight-20.14.17.jar`
- `avalon-20.12.6.5.jar`
- `photon-1.1.17.jar`
- `photon_and_epicfight-1.1.4.jar`
- `ldlib-1.0.52.a.jar`
- `cloth_config-11.1.136.jar`

上传 GitHub 时以 `sasuke-epicfight` 为仓库根目录，提交源码、资源、构建脚本和 `gradle/wrapper`（包含 Wrapper JAR）。运行目录、存档、备份和构建产物不会提交。

物品图标已包含在 `src/main/resources/assets/sasuke_epicfight/textures/item/kusanagi_icon.png`，构建不依赖外部 JPG。重新导入图片可运行 `python tools/import_item_icon.py "图片路径"`，需要 Pillow。
