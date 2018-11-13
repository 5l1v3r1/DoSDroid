package gr.kalymnos.sk3m3l10.ddosdroid.mvc_controllers.fragments.attack_list;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.AttackRepository;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.FakeAttackRepo;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_views.attack_lists_screen.AttackListViewMvc;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_views.attack_lists_screen.AttackListViewMvcImpl;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.DDoSAttack;

import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.DDoSAttack.AttackType.ATTACK_TYPE_KEY;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.DDoSAttack.AttackType.TYPE_NONE;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.DDoSAttack.CACHED_ATTACKS_KEY;
import static gr.kalymnos.sk3m3l10.ddosdroid.utils.ValidationUtils.bundleIsValidAndContainsKey;
import static gr.kalymnos.sk3m3l10.ddosdroid.utils.ValidationUtils.listHasItems;

public abstract class AttackListFragment extends Fragment implements AttackListViewMvc.OnAttackItemClickListener,
        AttackRepository.OnAttacksFetchListener {

    private static final String TAG = AttackListFragment.class.getSimpleName();

    private AttackListViewMvc viewMvc;
    protected AttackRepository attackRepo;
    private List<DDoSAttack> cachedAttacks;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        initializeViewMvc(inflater, container);
        return viewMvc.getRootView();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (cachedAttacksExist(savedInstanceState)) {
            viewMvc.bindAttacks(cachedAttacks);
        } else {
            viewMvc.showLoadingIndicator();
            initializeAttackRepo();
            fetchAttacksAccordingToType();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (listHasItems(cachedAttacks)) {
            outState.putParcelableArrayList(CACHED_ATTACKS_KEY, (ArrayList<? extends Parcelable>) cachedAttacks);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        removeFetchingAttacksListener();
    }

    @Override
    public void attacksFetchedSuccess(List<DDoSAttack> attacks) {
        cachedAttacks = attacks;
        viewMvc.hideLoadingIndicator();
        viewMvc.bindAttacks(cachedAttacks);
    }

    @Override
    public void attacksFetchedFail(String msg) {
        //  TODO: Display the error somewhere besides the toast
    }

    private boolean cachedAttacksExist(Bundle savedInstanceState) {
        if (bundleIsValidAndContainsKey(savedInstanceState, CACHED_ATTACKS_KEY)) {
            List<DDoSAttack> temp = savedInstanceState.getParcelableArrayList(CACHED_ATTACKS_KEY);
            if (listHasItems(temp)) {
                cachedAttacks = temp;
                return true;
            }
        }
        return false;
    }

    private void initializeViewMvc(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        viewMvc = new AttackListViewMvcImpl(inflater, container);
        viewMvc.setOnAttackItemClickListener(this);
    }

    protected abstract void fetchAttacksAccordingToType();

    private void initializeAttackRepo() {
        attackRepo = new FakeAttackRepo(getActivity());
        attackRepo.registerOnAttacksFetchListener(this);
    }

    protected int getAttacksType(Bundle bundle) {
        if (bundleIsValidAndContainsKey(bundle, ATTACK_TYPE_KEY)) {
            return bundle.getInt(ATTACK_TYPE_KEY);
        }
        return TYPE_NONE;
    }

    private void removeFetchingAttacksListener() {
        if (attackRepo != null) {
            attackRepo.unRegisterOnAttacksFetchListener();
            attackRepo = null;
        }
    }

    @Override
    public void onAttackItemClick(int position) {
        Toast.makeText(getContext(), cachedAttacks.get(position).getTargetWebsite(), Toast.LENGTH_SHORT).show();
    }

    /*
    *
    * I broke a switch statement that was exists in AllAttackListsViewMvcImpl.MyPagerAdapter
    * which was checking the text and returning a specific AttackListFragment subclass.
    *
    * Now AllAttackListsViewMvcImpl.MyPagerAdapter.getItemId() just creates an instance of
    * AttackListFragmentBuilder and call build() to return that specific AttackListFragment subclass
    * instance.
    *
    * This technique was used to clean the code. The justification lies in Uncled Bob's book
    * "Clean Code", chapter 3, page 39.
    *
    * */

    public interface AttackListFragmentBuilder {
        AttackListFragment build(String tabTitle, int attackType);
    }

    public static class AttackListFragmentBuilderImpl implements AttackListFragmentBuilder {

        @Override
        public AttackListFragment build(String tabTitle, int attackType) {
            AttackListFragment instance = getAttackListFragmentImplFromTabTitle(tabTitle);
            instance.setArguments(createFragmentArgs(attackType));
            return instance;
        }

        private AttackListFragment getAttackListFragmentImplFromTabTitle(String tabTitle) {
            switch (tabTitle) {
                // Titles were copied from R.arrays.network_technologies_titles
                case "Internet":
                    return new InternetAttackListFragment();
                case "WiFi P2P":
                    return new WiFiP2PAttackListFragment();
                case "NSD":
                    return new NSDAttackListFragment();
                case "Bluetooth":
                    return new BluetoothAttackListFragment();
                default:
                    throw new UnsupportedOperationException(TAG + " " + tabTitle + " is not a valid tab title");
            }
        }

        @NonNull
        protected static Bundle createFragmentArgs(int attacksType) {
            Bundle args = new Bundle();
            args.putInt(ATTACK_TYPE_KEY, attacksType);
            return args;
        }
    }
}
