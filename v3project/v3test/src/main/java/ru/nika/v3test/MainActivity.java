package ru.nika.v3test;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements ProgressCallback {
    private static final String PPTX_MIME =
        "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout root;
    private LinearLayout form;
    private EditText topicInput;
    private EditText countInput;
    private Button generate;
    private ProgressBar progress;
    private TextView status;
    private LinearLayout successActions;
    private LinearLayout failureActions;
    private File outputFile;
    private File logFile;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        root.setBackgroundColor(Color.rgb(244, 249, 251));
        scroll.addView(root, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Neon V3", 30, Color.rgb(1, 47, 66), true);
        root.addView(title);
        TextView subtitle = text(
            "Параллельный движок нового шаблона. Старый Presentation не вызывается.",
            16,
            Color.rgb(37, 38, 40),
            false
        );
        LinearLayout.LayoutParams subtitleParams = params();
        subtitleParams.topMargin = dp(8);
        subtitleParams.bottomMargin = dp(24);
        root.addView(subtitle, subtitleParams);

        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(18), dp(18), dp(18));
        form.setBackground(ShapeFactory.rounded(
            Color.WHITE, Color.rgb(182, 243, 255), dp(18), dp(1)
        ));
        root.addView(form, params());

        form.addView(text("Тема презентации", 15, Color.rgb(1, 47, 66), true));
        topicInput = new EditText(this);
        topicInput.setText("Цифровая ипотека");
        topicInput.setTextSize(18);
        topicInput.setSingleLine(false);
        topicInput.setMinLines(2);
        topicInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        topicInput.setBackground(ShapeFactory.rounded(
            Color.rgb(240, 252, 255), Color.rgb(128, 227, 255), dp(12), dp(1)
        ));
        LinearLayout.LayoutParams inputParams = params();
        inputParams.topMargin = dp(8);
        inputParams.bottomMargin = dp(18);
        form.addView(topicInput, inputParams);

        form.addView(text("Количество слайдов", 15, Color.rgb(1, 47, 66), true));
        countInput = new EditText(this);
        countInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        countInput.setText("12");
        countInput.setTextSize(18);
        countInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        countInput.setBackground(ShapeFactory.rounded(
            Color.rgb(240, 252, 255), Color.rgb(128, 227, 255), dp(12), dp(1)
        ));
        LinearLayout.LayoutParams countParams = params();
        countParams.topMargin = dp(8);
        countParams.bottomMargin = dp(22);
        form.addView(countInput, countParams);

        generate = primaryButton("Создать презентацию V3");
        generate.setOnClickListener(v -> startGeneration());
        form.addView(generate, params());

        progress = new ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        );
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = params();
        progressParams.topMargin = dp(22);
        root.addView(progress, progressParams);

        status = text("Готов к запуску", 16, Color.rgb(15, 92, 135), false);
        LinearLayout.LayoutParams statusParams = params();
        statusParams.topMargin = dp(12);
        root.addView(status, statusParams);

        successActions = new LinearLayout(this);
        successActions.setOrientation(LinearLayout.VERTICAL);
        successActions.setVisibility(View.GONE);
        LinearLayout.LayoutParams actionParams = params();
        actionParams.topMargin = dp(22);
        root.addView(successActions, actionParams);

        Button open = primaryButton("Открыть");
        open.setOnClickListener(v -> openOutput());
        successActions.addView(open, params());
        Button send = primaryButton("Отправить");
        send.setOnClickListener(v -> sendOutput());
        LinearLayout.LayoutParams sendParams = params();
        sendParams.topMargin = dp(12);
        successActions.addView(send, sendParams);

        failureActions = new LinearLayout(this);
        failureActions.setOrientation(LinearLayout.VERTICAL);
        failureActions.setVisibility(View.GONE);
        LinearLayout.LayoutParams failureParams = params();
        failureParams.topMargin = dp(18);
        root.addView(failureActions, failureParams);
        Button shareLog = primaryButton("Отправить журнал ошибки");
        shareLog.setOnClickListener(v -> shareLog());
        failureActions.addView(shareLog, params());

        setContentView(scroll);
    }

    private void startGeneration() {
        final String topic = topicInput.getText().toString().trim();
        if (topic.isEmpty()) {
            toast("Введите тему презентации.");
            return;
        }
        final int count;
        try {
            count = Integer.parseInt(countInput.getText().toString().trim());
        } catch (Exception error) {
            toast("Количество слайдов должно быть числом.");
            return;
        }
        if (count < 5 || count > 20) {
            toast("Для проверки V3 укажите от 5 до 20 слайдов.");
            return;
        }

        generate.setEnabled(false);
        topicInput.setEnabled(false);
        countInput.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(1);
        successActions.setVisibility(View.GONE);
        failureActions.setVisibility(View.GONE);
        status.setText("Запускаю новый движок V3…");
        outputFile = null;
        logFile = null;

        executor.execute(() -> {
            try {
                File work = new File(getFilesDir(), "v3");
                if (!work.isDirectory() && !work.mkdirs()) {
                    throw new IllegalStateException("Не удалось создать рабочую папку V3");
                }
                File template = new File(work, "presentation_template_v3.pptx");
                copyAsset("presentation_template_v3.pptx", template);

                File docs = new File(
                    getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "NeonV3"
                );
                if (!docs.isDirectory() && !docs.mkdirs()) {
                    throw new IllegalStateException("Не удалось создать папку результатов");
                }
                String stamp = new java.text.SimpleDateFormat(
                    "yyyyMMdd_HHmmss", java.util.Locale.US
                ).format(new java.util.Date());
                File requestedOutput = new File(
                    docs,
                    "Презентация_V3_" + safeFileName(topic) + "_" + stamp + ".pptx"
                );
                File requestedLog = new File(
                    docs,
                    "presentation_v3_" + stamp + ".jsonl"
                );
                logFile = requestedLog;

                String key = KeyProvider.resolve(this);
                Python py = Python.getInstance();
                PyObject module = py.getModule("neon_v3_engine");
                PyObject result = module.callAttr(
                    "run_engine",
                    template.getAbsolutePath(),
                    requestedOutput.getAbsolutePath(),
                    requestedLog.getAbsolutePath(),
                    topic,
                    count,
                    key,
                    this
                );
                JSONObject resultJson = new JSONObject(result.toString());
                if (!resultJson.optBoolean("ok", false)) {
                    throw new IllegalStateException(
                        resultJson.optString("message", "V3 завершился с ошибкой")
                    );
                }
                outputFile = new File(resultJson.getString("output"));
                logFile = new File(resultJson.getString("log"));
                if (!outputFile.isFile() || outputFile.length() < 4096) {
                    throw new IllegalStateException("V3 не создал целый PPTX");
                }
                runOnUiThread(this::showSuccess);
            } catch (Throwable error) {
                runOnUiThread(() -> showFailure(error));
            }
        });
    }

    @Override
    public void onProgress(int percent, String stage, String message) {
        runOnUiThread(() -> {
            progress.setProgress(Math.max(0, Math.min(100, percent)));
            status.setText(message == null || message.isEmpty()
                ? stage : message);
        });
    }

    private void showSuccess() {
        progress.setProgress(100);
        status.setText("Презентация V3 готова");
        form.setVisibility(View.GONE);
        failureActions.setVisibility(View.GONE);
        successActions.setVisibility(View.VISIBLE);
    }

    private void showFailure(Throwable error) {
        progress.setProgress(0);
        status.setText(
            "V3 остановлен без запуска старого движка.\n"
                + readable(error)
        );
        generate.setEnabled(true);
        topicInput.setEnabled(true);
        countInput.setEnabled(true);
        form.setVisibility(View.VISIBLE);
        successActions.setVisibility(View.GONE);
        failureActions.setVisibility(
            logFile != null && logFile.isFile() ? View.VISIBLE : View.GONE
        );
    }

    private void openOutput() {
        if (outputFile == null || !outputFile.isFile()) {
            toast("Готовый файл не найден.");
            return;
        }
        Uri uri = FileProvider.getUriForFile(
            this, getPackageName() + ".files", outputFile
        );
        Intent intent = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, PPTX_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            toast("На телефоне нет приложения для открытия PPTX.");
        }
    }

    private void sendOutput() {
        if (outputFile == null || !outputFile.isFile()) {
            toast("Готовый файл не найден.");
            return;
        }
        Uri uri = FileProvider.getUriForFile(
            this, getPackageName() + ".files", outputFile
        );
        Intent intent = new Intent(Intent.ACTION_SEND)
            .setType(PPTX_MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Отправить презентацию"));
    }

    private void shareLog() {
        if (logFile == null || !logFile.isFile()) {
            toast("Журнал ошибки не найден.");
            return;
        }
        Uri uri = FileProvider.getUriForFile(
            this, getPackageName() + ".files", logFile
        );
        Intent intent = new Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Отправить журнал V3"));
    }

    private void copyAsset(String name, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Не удалось создать папку шаблона");
        }
        File temporary = new File(target.getAbsolutePath() + ".tmp");
        try (InputStream input = getAssets().open(name);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Не удалось заменить шаблон V3");
        }
        if (!temporary.renameTo(target)) {
            throw new IllegalStateException("Не удалось сохранить шаблон V3");
        }
    }

    private static String safeFileName(String value) {
        String clean = value
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("\\s+", " ")
            .trim();
        if (clean.length() > 48) clean = clean.substring(0, 48).trim();
        return clean.isEmpty() ? "результат" : clean;
    }

    private static String readable(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String text = current.getMessage();
        if (text == null || text.trim().isEmpty()) {
            text = current.getClass().getSimpleName();
        }
        return text;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.12f);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinHeight(dp(58));
        button.setGravity(Gravity.CENTER);
        button.setBackground(ShapeFactory.rounded(
            Color.rgb(1, 47, 66), Color.rgb(128, 227, 255), dp(28), dp(1)
        ));
        return button;
    }

    private LinearLayout.LayoutParams params() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }
}
