package gr.kalymnos.sk3m3l10.ddosdroid.mvc_controllers.activities;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.webkit.URLUtil;
import android.widget.Toast;

import gr.kalymnos.sk3m3l10.ddosdroid.R;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_controllers.fragments.AttackCreationFragment;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_controllers.fragments.AttackInfoFragment;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_views.screen_attack_phase.CreateAttackViewMvc;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_views.screen_attack_phase.CreateAttackViewMvcImpl;

public class CreateAttackActivity extends AppCompatActivity implements AttackInfoFragment.OnBeginAttackButtonClickListener,
        AttackCreationFragment.OnAttackCreationButtonClickListener {

    private CreateAttackViewMvc viewMvc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupUi();
        getSupportFragmentManager().beginTransaction()
                .replace(viewMvc.getFragmentContainerId(), new AttackCreationFragment())
                .commit();
    }

    @Override
    public void onAttackCreationButtonClicked(String website) {
        boolean validUrlTyped = !TextUtils.isEmpty(website) && URLUtil.isValidUrl(website);
        if (validUrlTyped) {
            getSupportFragmentManager().beginTransaction()
                    .replace(viewMvc.getFragmentContainerId(), AttackInfoFragment.Builder.build(website))
                    .commit();
        } else {
            Snackbar.make(viewMvc.getRootView(), R.string.enter_valid_url_label,Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBeginAttackButtonClick() {
        Toast.makeText(this, "attack begin", Toast.LENGTH_SHORT).show();
    }

    private void setupUi() {
        viewMvc = new CreateAttackViewMvcImpl(LayoutInflater.from(this), null);
        setContentView(viewMvc.getRootView());
    }
}