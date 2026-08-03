using System;

namespace DOMRF.PowerPointPalette
{
    internal static class ColorApplicationService
    {
        private const int SelectionShapes = 2;
        private const int SelectionText = 3;
        private const int MsoTrue = -1;

        public static int Apply(object application, string mode, PaletteColor color)
        {
            if (application == null)
            {
                throw new InvalidOperationException("PowerPoint application object is not connected.");
            }

            if (color == null)
            {
                throw new ArgumentNullException(nameof(color));
            }

            dynamic app = application;
            dynamic activeWindow = app.ActiveWindow;
            if (activeWindow == null)
            {
                throw new InvalidOperationException("Откройте презентацию и выделите объект.");
            }

            dynamic selection = activeWindow.Selection;
            if (selection == null)
            {
                throw new InvalidOperationException("Сначала выделите фигуру, текст или линию.");
            }

            switch ((mode ?? string.Empty).Trim().ToLowerInvariant())
            {
                case "fill":
                    return ApplyFill(selection, color.OfficeRgb);
                case "text":
                    return ApplyText(selection, color.OfficeRgb);
                case "line":
                    return ApplyLine(selection, color.OfficeRgb);
                default:
                    throw new InvalidOperationException("Unknown palette mode: " + mode);
            }
        }

        private static int ApplyFill(dynamic selection, int officeRgb)
        {
            dynamic shapeRange = TryGetShapeRange(selection);
            if (shapeRange == null)
            {
                throw new InvalidOperationException("Для заливки выделите одну или несколько фигур.");
            }

            int applied = 0;
            int count = Convert.ToInt32(shapeRange.Count);
            for (int index = 1; index <= count; index++)
            {
                dynamic shape = shapeRange.Item(index);
                try
                {
                    shape.Fill.Visible = MsoTrue;
                    shape.Fill.Solid();
                    shape.Fill.ForeColor.RGB = officeRgb;
                    shape.Fill.Transparency = 0f;
                    applied++;
                }
                catch (Exception exception)
                {
                    DiagnosticsLog.AddIn("Fill skipped for shape " + index + ": " + exception.Message);
                }
            }

            if (applied == 0)
            {
                throw new InvalidOperationException("Выбранные объекты не поддерживают заливку.");
            }

            return applied;
        }

        private static int ApplyText(dynamic selection, int officeRgb)
        {
            int selectionType = SafeSelectionType(selection);
            if (selectionType == SelectionText)
            {
                bool changed = false;

                try
                {
                    selection.TextRange.Font.Color.RGB = officeRgb;
                    changed = true;
                }
                catch (Exception exception)
                {
                    DiagnosticsLog.AddIn("Legacy TextRange color failed: " + exception.Message);
                }

                try
                {
                    selection.TextRange2.Font.Fill.Visible = MsoTrue;
                    selection.TextRange2.Font.Fill.Solid();
                    selection.TextRange2.Font.Fill.ForeColor.RGB = officeRgb;
                    changed = true;
                }
                catch (Exception exception)
                {
                    DiagnosticsLog.AddIn("TextRange2 color failed: " + exception.Message);
                }

                if (changed)
                {
                    return 1;
                }
            }

            dynamic shapeRange = TryGetShapeRange(selection);
            if (shapeRange == null)
            {
                throw new InvalidOperationException("Для изменения цвета текста выделите текст или текстовую фигуру.");
            }

            int applied = 0;
            int count = Convert.ToInt32(shapeRange.Count);
            for (int index = 1; index <= count; index++)
            {
                dynamic shape = shapeRange.Item(index);
                bool changed = false;

                try
                {
                    if (Convert.ToInt32(shape.HasTextFrame) == MsoTrue && Convert.ToInt32(shape.TextFrame.HasText) == MsoTrue)
                    {
                        shape.TextFrame.TextRange.Font.Color.RGB = officeRgb;
                        changed = true;
                    }
                }
                catch (Exception exception)
                {
                    DiagnosticsLog.AddIn("TextFrame color skipped for shape " + index + ": " + exception.Message);
                }

                try
                {
                    if (Convert.ToInt32(shape.HasTextFrame) == MsoTrue && Convert.ToInt32(shape.TextFrame2.HasText) == MsoTrue)
                    {
                        shape.TextFrame2.TextRange.Font.Fill.Visible = MsoTrue;
                        shape.TextFrame2.TextRange.Font.Fill.Solid();
                        shape.TextFrame2.TextRange.Font.Fill.ForeColor.RGB = officeRgb;
                        changed = true;
                    }
                }
                catch (Exception exception)
                {
                    DiagnosticsLog.AddIn("TextFrame2 color skipped for shape " + index + ": " + exception.Message);
                }

                if (changed)
                {
                    applied++;
                }
            }

            if (applied == 0)
            {
                throw new InvalidOperationException("В выбранных объектах нет текста.");
            }

            return applied;
        }

        private static int ApplyLine(dynamic selection, int officeRgb)
        {
            dynamic shapeRange = TryGetShapeRange(selection);
            if (shapeRange == null)
            {
                throw new InvalidOperationException("Для изменения контура выделите одну или несколько фигур или линий.");
            }

            int applied = 0;
            int count = Convert.ToInt32(shapeRange.Count);
            for (int index = 1; index <= count; index++)
            {
                dynamic shape = shapeRange.Item(index);
                try
                {
                    shape.Line.Visible = MsoTrue;
                    shape.Line.ForeColor.RGB = officeRgb;
                    applied++;
                }
                catch (Exception exception)
                {
                    DiagnosticsLog.AddIn("Line skipped for shape " + index + ": " + exception.Message);
                }
            }

            if (applied == 0)
            {
                throw new InvalidOperationException("Выбранные объекты не поддерживают контур.");
            }

            return applied;
        }

        private static dynamic TryGetShapeRange(dynamic selection)
        {
            try
            {
                int type = SafeSelectionType(selection);
                if (type != SelectionShapes && type != SelectionText)
                {
                    return null;
                }

                return selection.ShapeRange;
            }
            catch
            {
                return null;
            }
        }

        private static int SafeSelectionType(dynamic selection)
        {
            try
            {
                return Convert.ToInt32(selection.Type);
            }
            catch
            {
                return -1;
            }
        }
    }
}
