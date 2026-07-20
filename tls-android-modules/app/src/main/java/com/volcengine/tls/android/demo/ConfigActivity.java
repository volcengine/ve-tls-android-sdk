package com.volcengine.tls.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Properties;

public class ConfigActivity extends Activity {
    private EditText endPointInput;
    private EditText regionInput;
    private EditText akInput;
    private EditText skInput;
    private EditText tokenInput;
    private EditText topicIdInput;
    private EditText compressInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Properties props = ConfigLoader.load(this);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("TLS 配置");
        title.setTextSize(22f);
        layout.addView(title);

        TextView hint = new TextView(this);
        File targetFile = ConfigLoader.resolveConfigFile(this);
        hint.setText("填写最小必需字段，保存后发送页和 Benchmark 页会共用这份配置。\n保存位置：" + targetFile.getAbsolutePath());
        hint.setPadding(0, 20, 0, 30);
        layout.addView(hint);

        endPointInput = addField(layout, "endPoint", "https://tls-cn-xxx.volces.com", ConfigLoader.get(props, "endPoint"), false);
        regionInput = addField(layout, "region", "cn-xxx", ConfigLoader.get(props, "region"), false);
        akInput = addField(layout, "ak", "AccessKeyId", ConfigLoader.get(props, "ak"), false);
        skInput = addField(layout, "sk", "SecretAccessKey", ConfigLoader.get(props, "sk"), true);
        tokenInput = addField(layout, "token", "可为空", ConfigLoader.get(props, "token"), true);
        topicIdInput = addField(layout, "topicId", "topic-example", ConfigLoader.get(props, "topicId"), false);
        compressInput = addField(layout, "compress", "留空 / lz4 / none", ConfigLoader.get(props, "compress"), false);

        Button saveBtn = new Button(this);
        saveBtn.setText("保存");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig(false);
            }
        });
        layout.addView(saveBtn);

        Button saveAndBackBtn = new Button(this);
        saveAndBackBtn.setText("保存并返回");
        saveAndBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveConfig(true);
            }
        });
        layout.addView(saveAndBackBtn);

        scrollView.addView(layout);
        setContentView(scrollView);
    }

    private EditText addField(LinearLayout layout, String label, String hint, String value, boolean secret) {
        TextView tv = new TextView(this);
        tv.setText(label);
        layout.addView(tv);

        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        if (secret) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        layout.addView(input);
        return input;
    }

    private void saveConfig(boolean finishAfterSave) {
        Properties props = new Properties();
        props.setProperty("endPoint", textOf(endPointInput));
        props.setProperty("region", textOf(regionInput));
        props.setProperty("ak", textOf(akInput));
        props.setProperty("sk", textOf(skInput));
        props.setProperty("token", textOf(tokenInput));
        props.setProperty("topicId", textOf(topicIdInput));
        props.setProperty("compress", ConfigLoader.normalizeCompressValue(textOf(compressInput)));

        if (!ConfigLoader.hasRequiredConfig(props)) {
            toast("endPoint / region / ak / sk / topicId 为必填");
            return;
        }

        try {
            ConfigLoader.save(this, props);
            toast("保存成功");
            if (finishAfterSave) {
                finish();
            }
        } catch (Exception e) {
            toast("保存失败: " + e.getMessage());
        }
    }

    private static String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
