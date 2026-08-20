# PowerPoint 15 Colors

Однофайловый установщик управляемой COM-надстройки PowerPoint с фирменной палитрой из 15 цветов.

## Что устанавливается

- вкладка `ПАЛИТРА` в ленте PowerPoint;
- 15 цветов в разделах `Основные`, `Дополнительные`, `Статусные`;
- команды `ЗАЛИВКА`, `ТЕКСТ`, `КОНТУР`;
- автоматическая загрузка при каждом запуске PowerPoint;
- подробные установочный и рабочий логи.

## Технология

C# / .NET Framework 4.8, `IDTExtensibility2`, `IRibbonExtensibility`, RibbonX и системная регистрация через `RegAsm`. VBA, PPAM, VSTO и `AccessVBOM` не используются.

Установщик сообщает об успехе только после того, как PowerPoint подтвердил подключение COM-надстройки и вызвал загрузку RibbonX-интерфейса.

## Разработка

Рабочая ветка: `powerpoint-palette-addin`.

Документы:

- `docs/TECH_SPEC.md`;
- `docs/ARCHITECTURE.md`;
- `docs/TEST_PLAN.md`.

Сборка GitHub Actions публикует один файл `PowerPoint_15_Colors_Setup.exe` и проверяет self-test, COM-метаданные и `CodeBase`, записываемый RegAsm.
