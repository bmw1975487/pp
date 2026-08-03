# Архитектура

## Почему предыдущий PPAM-подход был исключён

Прежний установщик пытался на компьютере пользователя создать макро-надстройку `.ppam`, открыть объектную модель VBA и программно добавить модуль. Этот путь зависит от Trust Center, `AccessVBOM`, состояния процесса PowerPoint и корректной работы VBProject COM API. Ошибка `0x80070006: Неверный дескриптор` возникла именно внутри VBProject при переименовании созданного компонента. Такой механизм не должен использоваться для конечного установщика.

Новая версия не создаёт VBA-проект и не касается `VBProject` вообще.

## Компоненты одного EXE

### 1. InstallerHost

Запускается обычным двойным щелчком.

Функции:

- проверка PowerPoint;
- ожидание закрытия PowerPoint;
- очистка только наших старых регистраций;
- копирование самого себя в постоянный каталог;
- запуск системного `RegAsm.exe`;
- запись Office Add-ins registry;
- запуск PowerPoint и проверка подключения;
- логирование и показ результата.

### 2. PaletteAddIn

COM-видимый класс внутри той же EXE-сборки.

Реализует:

- `IDTExtensibility2`;
- `IRibbonExtensibility`;
- публичные Ribbon callbacks.

Сохраняет ссылку на экземпляр PowerPoint из `OnConnection` и использует late binding (`dynamic`) для работы с активным выделением. За счёт этого не требуется устанавливать Office Primary Interop Assemblies рядом с EXE.

### 3. RibbonFactory

Строит RibbonX XML из единственного массива цветов. Это исключает расхождение между интерфейсом и кодом применения.

### 4. PaletteService

Содержит 15 неизменяемых записей:

- категория;
- HEX;
- отображаемое имя;
- вычисленный Office RGB.

### 5. ColorApplicationService

Применяет цвет к выделению:

- `Fill.ForeColor.RGB`;
- `TextRange.Font.Color.RGB` и `TextRange2.Font.Fill.ForeColor.RGB`;
- `Line.ForeColor.RGB`.

Все COM-вызовы изолированы и защищены обработкой исключений.

### 6. SwatchImageFactory

Создаёт маленькие цветные изображения для кнопок Ribbon и преобразует их в `IPictureDisp`, ожидаемый Office.

### 7. Diagnostics

Два независимых лога:

- установщик;
- загруженная надстройка.

В `OnConnection`, `GetCustomUI` и каждом callback есть отдельные записи. Это позволяет отличить:

1. проблему COM-регистрации;
2. проблему загрузки Office;
3. ошибку RibbonX;
4. ошибку применения цвета.

## Регистрация

### COM

Используется системный RegAsm той же разрядности, что и PowerPoint:

- x64: `%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\RegAsm.exe`;
- x86: `%WINDIR%\Microsoft.NET\Framework\v4.0.30319\RegAsm.exe`.

Команда:

```text
RegAsm.exe <installed-exe> /nologo /codebase
```

### PowerPoint

```text
HKCU\Software\Microsoft\Office\PowerPoint\Addins\DOMRF.PowerPointPalette
```

Значения:

```text
FriendlyName    REG_SZ    DOM.RF — 15 цветов
Description     REG_SZ    Фирменная палитра PowerPoint
LoadBehavior    REG_DWORD 3
CommandLineSafe REG_DWORD 0
```

## Проверка после установки

Установщик создаёт `PowerPoint.Application`, вызывает `COMAddIns.Update()`, получает элемент по ProgID, задаёт `Connect=True`, ждёт и повторно читает `Connect`.

Успех фиксируется только при одновременном выполнении условий:

- COMAddIn найден;
- `Connect=True`;
- в логе надстройки есть запись `OnConnection`;
- `GetCustomUI` был вызван после появления окна презентации.

## Обновление

При повторном запуске EXE:

1. закрывается PowerPoint;
2. предыдущая установленная копия снимается с COM-регистрации;
3. файл заменяется;
4. выполняется новая регистрация;
5. состояние проверяется заново.

Это обеспечивает идемпотентную установку.
