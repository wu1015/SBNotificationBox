package com.wu1015.sbnotificationbox.mailsend;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.wu1015.sbnotificationbox.R;

import java.util.ArrayList;
import java.util.List;

public class FilterSettingsActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleGroupMode;
    private MaterialButton btnDisabled;
    private MaterialButton btnWhitelist;
    private MaterialButton btnBlacklist;
    private MaterialCardView cardPackageList;
    private TextView textViewListLabel;
    private ListView listViewPackages;
    private MaterialButton btnAddPackage;

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
        toggleGroupMode = findViewById(R.id.toggleGroupMode);
        btnDisabled = findViewById(R.id.btnDisabled);
        btnWhitelist = findViewById(R.id.btnWhitelist);
        btnBlacklist = findViewById(R.id.btnBlacklist);
        cardPackageList = findViewById(R.id.cardPackageList);
        textViewListLabel = findViewById(R.id.textViewListLabel);
        listViewPackages = findViewById(R.id.listViewPackages);
        btnAddPackage = findViewById(R.id.btnAddPackage);
    }

    private void loadCurrentSettings() {
        currentMode = PackageFilterManager.getFilterMode(this);

        switch (currentMode) {
            case WHITELIST:
                toggleGroupMode.check(R.id.btnWhitelist);
                break;
            case BLACKLIST:
                toggleGroupMode.check(R.id.btnBlacklist);
                break;
            case DISABLED:
            default:
                toggleGroupMode.check(R.id.btnDisabled);
                break;
        }

        refreshPackageList();
        updateListVisibility();
    }

    private void setupListeners() {
        toggleGroupMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return; // 忽略取消选中

            if (checkedId == R.id.btnWhitelist) {
                currentMode = PackageFilterManager.FilterMode.WHITELIST;
            } else if (checkedId == R.id.btnBlacklist) {
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
            cardPackageList.setVisibility(View.GONE);
        } else {
            cardPackageList.setVisibility(View.VISIBLE);
            textViewListLabel.setText(
                    currentMode == PackageFilterManager.FilterMode.WHITELIST
                            ? R.string.filter_whitelist
                            : R.string.filter_blacklist);
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
                view.setPadding(16, 16, 16, 16);
                view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                return view;
            }
        };
        listViewPackages.setAdapter(adapter);

        View emptyView = findViewById(R.id.textViewEmpty);
        listViewPackages.setEmptyView(emptyView);
    }

    private void showAddPackageDialog() {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint("Package name (e.g. com.example.app)");
        til.setPadding(32, 24, 32, 0);

        TextInputEditText input = new TextInputEditText(this);
        input.setHint(R.string.package_name_hint);
        til.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_package)
                .setView(til)
                .setPositiveButton("Add", (dialog, which) -> {
                    String packageName = input.getText().toString().trim();
                    if (packageName.isEmpty()) {
                        Toast.makeText(this, "Please enter a package name", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (currentMode == PackageFilterManager.FilterMode.WHITELIST) {
                        PackageFilterManager.addToWhitelist(this, packageName);
                    } else {
                        PackageFilterManager.addToBlacklist(this, packageName);
                    }

                    refreshPackageList();
                    Toast.makeText(this, "Added: " + packageName, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteDialog(String packageName) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Package")
                .setMessage("Remove \"" + packageName + "\" from the filter list?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (currentMode == PackageFilterManager.FilterMode.WHITELIST) {
                        PackageFilterManager.removeFromWhitelist(this, packageName);
                    } else {
                        PackageFilterManager.removeFromBlacklist(this, packageName);
                    }
                    refreshPackageList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
