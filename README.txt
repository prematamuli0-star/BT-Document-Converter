# BT Document Converter — Full Code (Excel + Crop phase)

Replace these two files in the existing GitHub repository:

- `app/src/main/java/com/bt/documentconverter/OfflinePdfEngine.kt`
- `app/src/main/java/com/bt/documentconverter/MainActivity.kt`

No extra Gradle dependency is required for this basic `.xlsx` reader: it uses Android's XML pull parser and ZIP support.

## Included
- Image to PDF
- Text to PDF
- Excel `.xlsx` to PDF (first worksheet; basic cell text only)
- Merge PDF
- Extract page
- Rotate all pages
- Crop all pages using common margins
- Delete page, add watermark, rewrite/optimize foundation in engine

## Excel limitations
- Only modern `.xlsx` is supported; legacy `.xls` is not.
- Only first worksheet is converted.
- Basic cell text/shared strings are read. Excel styling, charts, images, formula recalculation, print areas and advanced layouts are not preserved.
- Very large spreadsheets may require additional memory handling.

## Crop limitations
Crop applies the same margins to every page. Values are PDF points; 72 points = 1 inch. It changes the visible crop box; it does not securely erase hidden page content.

## Important
This source has not been compiled or device-tested in this package generation. Run GitHub Actions before installing. If the build fails, share the complete first compiler error.
