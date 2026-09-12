# 第三方资源许可

## Phosphor Icons

本 Android 应用使用 [Phosphor Icons 官方 core 仓库](https://github.com/phosphor-icons/core)中的下列成品图标，许可为 MIT。

- 固定来源版本：[`2b75f3ad12b420c9504ef05df8d2564a28f8500e`](https://github.com/phosphor-icons/core/tree/2b75f3ad12b420c9504ef05df8d2564a28f8500e)。
- 原始文件目录：`assets/`；完整路径列于下表。
- 适配方式：仅将 SVG 的 `path d` 原样映射到 Android VectorDrawable 的 `android:pathData`，将 `opacity` 映射为 `android:fillAlpha`；保留 256 × 256 viewport，默认显示尺寸 24dp。
- 原始 `currentColor` 使用黑色作为可着色蒙版，由 Compose 的图标 tint 决定实际颜色；未重绘图标、修改路径几何或引入整库运行时依赖。

| Android 资源 | 官方源文件（相对于 `assets/`） |
| --- | --- |
| `ic_phosphor_squares_four.xml` | [`regular/squares-four.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/regular/squares-four.svg) |
| `ic_phosphor_squares_four_fill.xml` | [`fill/squares-four-fill.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/fill/squares-four-fill.svg) |
| `ic_phosphor_chart_line_up.xml` | [`regular/chart-line-up.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/regular/chart-line-up.svg) |
| `ic_phosphor_chart_line_up_fill.xml` | [`fill/chart-line-up-fill.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/fill/chart-line-up-fill.svg) |
| `ic_phosphor_user_circle.xml` | [`regular/user-circle.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/regular/user-circle.svg) |
| `ic_phosphor_user_circle_fill.xml` | [`fill/user-circle-fill.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/fill/user-circle-fill.svg) |
| `ic_phosphor_file_text_duotone.xml` | [`duotone/file-text-duotone.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/duotone/file-text-duotone.svg) |
| `ic_phosphor_magnifying_glass_duotone.xml` | [`duotone/magnifying-glass-duotone.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/duotone/magnifying-glass-duotone.svg) |
| `ic_phosphor_upload_simple_duotone.xml` | [`duotone/upload-simple-duotone.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/duotone/upload-simple-duotone.svg) |
| `ic_phosphor_brain_duotone.xml` | [`duotone/brain-duotone.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/duotone/brain-duotone.svg) |
| `ic_phosphor_arrows_clockwise.xml` | [`regular/arrows-clockwise.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/regular/arrows-clockwise.svg) |
| `ic_phosphor_warning_circle.xml` | [`regular/warning-circle.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/regular/warning-circle.svg) |
| `ic_phosphor_lock_simple.xml` | [`regular/lock-simple.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/regular/lock-simple.svg) |
| `ic_phosphor_arrow_right.xml` | [`regular/arrow-right.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/regular/arrow-right.svg) |
| `ic_phosphor_arrows_out.xml` | [`regular/arrows-out.svg`](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/assets/regular/arrows-out.svg) |

### 原始 MIT 许可全文

来源：[固定版本 LICENSE](https://github.com/phosphor-icons/core/blob/2b75f3ad12b420c9504ef05df8d2564a28f8500e/LICENSE)。

```text
MIT License

Copyright (c) 2023 Phosphor Icons

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
