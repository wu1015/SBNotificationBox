package com.wu1015.sbnotificationbox.mailsend;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.wu1015.sbnotificationbox.R;

import java.util.ArrayList;
import java.util.List;

public class FilterSettingsActivity extends AppCompatActivity {

    private RadioGroup radioGroupMode;
    private RadioButton radioDisabled;
    private RadioButton radioWhitelist;
    private RadioButton radioBlacklist;
    private TextView textViewListLabel;
    private ListView listViewPackages;
    private Button btnAddPackage;

    private ArrayAdapter<String> adapter;
    private PackageFilterManager.FilterMode currentMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_filter_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        loadCurrentSettings();
        setupListeners();
    }

    private void initViews() {
        radioGroupMode = findViewById(R.id.radioGroupMode);
        radioDisabled = findViewById(R.id.radioDisabled);
        radioWhitelist = findViewById(R.id.radioWhitelist);
        radioBlacklist = findViewById(R.id.radioBlacklist);
        textViewListLabel = findViewById(R.id.textViewListLabel);
        listViewPackages = findViewById(R.id.listViewPackages);
        btnAddPackage = findViewById(R.id.btnAddPackage);
    }

    private void loadCurrentSettings() {
        currentMode = PackageFilterManager.getFilterMode(this);

        switch (currentMode) {
            case WHITELIST:
                radioWhitelist.setChecked(true);
                break;
            case BLACKLIST:
                radioBlacklist.setChecked(true);
                break;
            case DISABLED:
            default:
                radioDisabled.setChecked(true);
                break;
        }

        refreshPackageList();
        updateListVisibility();
    }

    private void setupListeners() {
        radioGroupMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioWhitelist) {
                currentMode = PackageFilterManager.FilterMode.WHITELIST;
            } else if (checkedId == R.id.radioBlacklist) {
                currentMode = PackageFilterManager.FilterMode.BLACKLIST;
            } else {
                currentMode = PackageFilterManager.FilterMode.DISABLED;
            }

            PackageFilterManager.setFilterMode(this, currentMode);
            refreshPackageList();
            updateListVisibility();
        });

        btnAddPackage.setOnClickListener(v -> showAddPackageDialog());

        listViewPackages.setOnItemLongClickListener((parent, view, position, id) -> {
            String packageName = adapter.getItem(position);
            if (packageName != null) {
                showDeleteDialog(packageName);
            }
            return true;
        });
    }

    private void updateListVisibility() {
        if (currentMode == PackageFilterManager.FilterMode.DISABLED) {
            textViewListLabel.setVisibility(View.GONE);
            listViewPackages.setVisibility(View.GONE);
            btnAddPackage.setVisibility(View.GONE);
        } else {
            textViewListLabel.setVisibility(View.VISIBLE);
            listViewPackages.setVisibility(View.VISIBLE);
            btnAddPackage.setVisibility(View.VISIBLE);

            if (currentMode == PackageFilterManager.FilterMode.WHITELIST) {
                textViewListLabel.setText(R.string.filter_whitelist);
            } else {
                textViewListLabel.setText(R.string.filter_blacklist);
            }
        }
    }

    private void refreshPackageList() {
        List<String> packages;

        if (currentMode == PackageFilterManager.FilterMode.WHITELIST) {
            packages = PackageFilterManager.getWhitelistSorted(this);
        } else if (currentMode == PackageFilterManager.FilterMode.BLACKLIST) {
            packages = PackageFilterManager.getBlacklistSorted(this);
        } else {
            packages = new ArrayList<>();
        }

        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, packages) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setPadding(16, 24, 16, 24);
                return view;
            }
        };
        listViewPackages.setAdapter(adapter);

        // 空列表提示
        View emptyView = findViewById(R.id.textViewEmpty);
        listViewPackages.setEmptyView(emptyView);
    }

    private void showAddPackageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_package);

        final EditText input = new EditText(this);
        input.setHint(R.string.package_name_hint);
        input.setPadding(32, 16, 32, 16);
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String packageName = input.getText().toString().trim();
            if (packageName.isEmpty()) {
                Toast.makeText(this, "Please enter a package name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentMode == PackageFilterManager.FilterMode.WHITELIST) {
                PackageFilterManager.addToWhitelist(this, packageName);
            } else if (currentMode == PackageFilterManager.FilterMode.BLACKLIST) {
                PackageFilterManager.addToBlacklist(this, packageName);
            }

            refreshPackageList();
            Toast.makeText(this, "Added: " + packageName, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDeleteDialog(String packageName) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Package")
                .setMessage("Remove \"" + packageName + "\" from the filter list?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (currentMode == PackageFilterManager.FilterMode.WHITELIST) {
                        PackageFilterManager.removeFromWhitelist(this, packageName);
                    } else if (currentMode == PackageFilterManager.FilterMode.BLACKLIST) {
                        PackageFilterManager.removeFromBlacklist(this, packageName);
                    }
                    refreshPackageList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
