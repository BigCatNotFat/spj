package com.example.spj;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * 全屏编辑器活动
 * 用于提供更舒适的编辑体验
 */
public class FullscreenEditorActivity extends AppCompatActivity {

    // 用于传递和返回文本内容的意图键
    public static final String EXTRA_TEXT_CONTENT = "com.example.spj.EXTRA_TEXT_CONTENT";
    public static final String EXTRA_EDITED_CONTENT = "com.example.spj.EXTRA_EDITED_CONTENT";

    private EditText editTextContent;
    private String originalContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_editor);

        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.edit_notes);
        }

        // 初始化视图
        editTextContent = findViewById(R.id.editTextContent);

        // 获取传递的文本内容
        originalContent = getIntent().getStringExtra(EXTRA_TEXT_CONTENT);
        if (originalContent != null) {
            editTextContent.setText(originalContent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_editor, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            // 用户点击了返回按钮，询问是否保存更改
            showSaveConfirmationDialog();
            return true;
        } else if (id == R.id.action_save) {
            // 用户点击了保存按钮
            saveAndFinish();
            return true;
        } else if (id == R.id.action_reset) {
            // 用户点击了重置按钮
            resetContent();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // 用户按了返回键，询问是否保存更改
        showSaveConfirmationDialog();
    }

    /**
     * 显示保存确认对话框
     */
    private void showSaveConfirmationDialog() {
        // 检查内容是否有变化
        String currentContent = editTextContent.getText().toString();
        if (currentContent.equals(originalContent)) {
            // 内容没有变化，直接关闭
            finish();
            return;
        }

        // 内容有变化，显示确认对话框
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.save_changes)
                .setMessage(R.string.save_changes_message)
                .setPositiveButton(R.string.save, (dialog, which) -> saveAndFinish())
                .setNegativeButton(R.string.discard, (dialog, which) -> finish())
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    /**
     * 保存内容并关闭活动
     */
    private void saveAndFinish() {
        String content = editTextContent.getText().toString();

        // 创建包含编辑内容的结果意图
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_EDITED_CONTENT, content);
        setResult(RESULT_OK, resultIntent);

        // 显示提示并关闭
        Toast.makeText(this, R.string.changes_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * 重置内容为原始内容
     */
    private void resetContent() {
        if (originalContent != null) {
            editTextContent.setText(originalContent);
            Toast.makeText(this, R.string.content_reset, Toast.LENGTH_SHORT).show();
        }
    }
}