/*
 * Copyright (c) 2020 Proton Technologies AG
 * 
 * This file is part of ProtonMail.
 * 
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.activities.dialogs;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import butterknife.BindView;
import butterknife.OnClick;
import ch.protonmail.android.R;
import ch.protonmail.android.adapters.LabelColorsAdapter;
import ch.protonmail.android.adapters.LabelsAdapter;
import ch.protonmail.android.api.models.room.messages.Label;
import ch.protonmail.android.api.models.room.messages.MessagesDatabase;
import ch.protonmail.android.api.models.room.messages.MessagesDatabaseFactory;
import ch.protonmail.android.core.ProtonMailApplication;
import ch.protonmail.android.utils.UiUtil;
import ch.protonmail.android.utils.UserUtils;
import ch.protonmail.android.utils.extensions.TextExtensions;
import ch.protonmail.android.views.ThreeStateButton;
import ch.protonmail.android.views.ThreeStateCheckBox;

/**
 * Created by dkadrikj on 19.7.15.
 */
public class ManageLabelsDialogFragment extends AbstractDialogFragment implements AdapterView.OnItemClickListener {

    public static final String ARGUMENT_CHECKED_LABELS = "ch.protonmail.android.ARG_CHECKED_LABELS";
    public static final String ARGUMENT_NUMBER_SELECTED_MESSAGES = "ch.protonmail.android.ARG_NUMBER_SELECTED_MESSAGES";
    public static final String ARGUMENT_MESSAGE_IDS = "ch.protonmail.android.ARG_MESSAGE_IDS";
    public static final String ARGUMENT_SHOW_CHECKBOXES = "ch.protonmail.android.ARG_SHOW_CHECKBOXES";

    @Nullable
    @BindView(R.id.progress_bar)
    ProgressBar mProgressBar;
    @BindView(R.id.add_label_container)
    View mAddLabelContainer;
    @BindView(R.id.label_name)
    EditText mLabelName;
    @BindView(R.id.labels_list_view)
    ListView mList;
    @BindView(R.id.labels_grid_view)
    GridView mColorsGrid;
    @BindView(R.id.done)
    Button mDone;
    @BindView(R.id.labels_dialog_title)
    TextView mTitle;
    @BindView(R.id.also_archive)
    ThreeStateCheckBox mArchiveCheckbox;
    @BindView(R.id.archive_container)
    View mArchiveContainer;
    @BindView(R.id.no_labels)
    View mNoLabelsView;
    @BindView(R.id.list_divider)
    View mListDivider;

    private ILabelsChangeListener mLabelStateChangeListener;
    private ILabelCreationListener mLabelCreationListener;
    private LabelsAdapter mAdapter;
    private LabelColorsAdapter mColorsAdapter;
    private List<LabelsAdapter.LabelItem> mLabels;
    private List<String> mCheckedLabels;
    private HashMap<String, Integer> mAllLabelsMap;
    private List<String> mMessageIds;
    private String mSelectedNewLabelColor;
    private int[] mColorOptions;
    private boolean mCreationMode = false;
    private boolean mShowCheckboxes;
    private int mCurrentSelection = -1;

    /**
     * Instantiates a new fragment of this class.
     *
     * @param checkedLabels pass null if you do not need labels checked
     * @return new instance of {@link ManageLabelsDialogFragment}
     */
    public static ManageLabelsDialogFragment newInstance(Set<String> checkedLabels, HashMap<String, Integer> numberOfMessagesSelected,
                                                         ArrayList<String> messageIds, boolean showCheckboxes) {
        ManageLabelsDialogFragment fragment = new ManageLabelsDialogFragment();
        Bundle extras = new Bundle();
        extras.putStringArrayList(ARGUMENT_CHECKED_LABELS, new ArrayList<>(checkedLabels));
        extras.putSerializable(ARGUMENT_NUMBER_SELECTED_MESSAGES, numberOfMessagesSelected);
        extras.putStringArrayList(ARGUMENT_MESSAGE_IDS, messageIds);
        extras.putBoolean(ARGUMENT_SHOW_CHECKBOXES, showCheckboxes);
        fragment.setArguments(extras);
        return fragment;
    }

    @Override
    public String getFragmentKey() {
        return "ProtonMail.ManageLabelsFragment";
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mLabelStateChangeListener = (ILabelsChangeListener) activity;
        } catch (ClassCastException e) {
            // not throwing error, since the user of this dialog is not obligated to listen for
            // labels state change
        }
        try {
            mLabelCreationListener = (ILabelCreationListener) activity;
        } catch (ClassCastException e) {
            // not throwing error since the user of this fragment may not want to create new labels
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.dialog_fragment_manage_labels;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ProtonMailApplication.getApplication().getAppComponent().inject(this);
        // TODO: check for  Channel is unrecoverably broken and will be disposed
        Bundle extras = getArguments();
        if (extras != null && extras.containsKey(ARGUMENT_CHECKED_LABELS)) {
            mCheckedLabels = getArguments().getStringArrayList(ARGUMENT_CHECKED_LABELS);
            mAllLabelsMap = (HashMap<String, Integer>) getArguments().getSerializable(ARGUMENT_NUMBER_SELECTED_MESSAGES);
            mMessageIds = getArguments().getStringArrayList(ARGUMENT_MESSAGE_IDS);
        } else {
            mCheckedLabels = new ArrayList<>();
            mMessageIds = null;
        }
        mShowCheckboxes = getArguments().getBoolean(ARGUMENT_SHOW_CHECKBOXES);
    }

    AdapterView.OnItemLongClickListener labelItemLongClick = (parent, view, position, id) -> false;

    @Override
    protected void initUi(final View rootView) {
        mList.setOnItemLongClickListener(labelItemLongClick);
        if (mLabelCreationListener != null) {
            // TODO:
        }
        mColorsGrid.setOnItemClickListener(this);
        mArchiveCheckbox.getButton().numberOfStates = 2;
        MessagesDatabase messagesDatabase = MessagesDatabaseFactory.Companion.getInstance(getContext().getApplicationContext()).getDatabase();
        messagesDatabase.getAllLabels().observe(this,new LabelsObserver());
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                Rect r = new Rect();
                rootView.getWindowVisibleDisplayFrame(r);

                Window window = getDialog().getWindow();
                if (window != null) {
                    window.setGravity(Gravity.CENTER);
                }
            }
        });

        mLabelName.addTextChangedListener(newLabelWatcher);
        setDialogTitle(R.string.labels_title_apply);
        setDoneTitle(R.string.label_apply);
        if (!mShowCheckboxes) {
            mArchiveContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected int getStyleResource() {
        return R.style.AppTheme_Dialog_Labels;
    }

    @OnClick(R.id.close)
    public void onCloseClicked() {
        dismissAllowingStateLoss();
    }

    @OnClick(R.id.done)
    public void onDoneClicked() {
        if (mCreationMode) {
            if (TextUtils.isEmpty(mSelectedNewLabelColor)) {
                TextExtensions.showToast(getActivity(), R.string.please_choose_color, Toast.LENGTH_SHORT);
            } else {
                mCreationMode = false;
                onSaveClicked();
            }
        } else {
            List<String> checkedLabelIds = getCheckedLabels();
            int maxLabelsAllowed = UserUtils.getMaxAllowedLabels(ProtonMailApplication.getApplication().getUserManager());
            if (checkedLabelIds.size() > maxLabelsAllowed) {
                if (isAdded()) {
                    TextExtensions.showToast(getActivity(), String.format(getString(R.string.max_labels_selected), maxLabelsAllowed), Toast.LENGTH_SHORT);
                }
                return;
            }
            if (mShowCheckboxes && mLabelStateChangeListener != null) {
                if (mArchiveCheckbox.getState() == ThreeStateButton.STATE_CHECKED ||
                        mArchiveCheckbox.getState() == ThreeStateButton.STATE_PRESSED) {
                    // also archive
                    mLabelStateChangeListener.onLabelsChecked(getCheckedLabels(), mMessageIds == null ? null : getUnchangedLabels(), mMessageIds, mMessageIds);
                } else {
                    mLabelStateChangeListener.onLabelsChecked(getCheckedLabels(), mMessageIds == null ? null :  getUnchangedLabels(), mMessageIds);
                }
            } else if (!mShowCheckboxes) {
                mLabelCreationListener.onLabelsDeleted(getCheckedLabels());
            }
            dismissAllowingStateLoss();
        }
    }

    private TextWatcher newLabelWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // NOOP
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (TextUtils.isEmpty(mLabelName.getText())) {
                mCreationMode = false;
                mColorsGrid.setVisibility(View.GONE);
                mList.setVisibility(View.VISIBLE);
                mLabelName.setVisibility(View.VISIBLE);
                UiUtil.hideKeyboard(getActivity(), mLabelName);
                setDoneTitle(R.string.label_apply);
                setDialogTitle(R.string.labels_title_apply);
                mCurrentSelection = -1;
            } else {
                if (!mCreationMode) {
                    mCreationMode = true;
                    inflateColors();
                    randomCheck();
                    mAddLabelContainer.setVisibility(View.VISIBLE);
                    mDone.setText(getString(R.string.label_add));
                    mTitle.setText(getString(R.string.labels_title_add));
                }
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
            // NOOP
            mDone.setClickable(true);
        }
    };

    private void setDoneTitle(@StringRes int doneRes) {
        if (doneRes == R.string.label_apply) {
            if (mShowCheckboxes) {
                mDone.setText(getString(doneRes));
            } else {
                mDone.setText(getString(R.string.label_delete));
                mDone.setTextColor(Color.RED);
            }
        } else {
            if (!mShowCheckboxes) {
                mDone.setText(getString(doneRes));
            }
        }
    }

    private void setDialogTitle(@StringRes int titleRes) {
        if (titleRes == R.string.labels_title_apply) {
            if (mShowCheckboxes) {
                mTitle.setText(getString(titleRes));
            } else {
                mTitle.setText(getString(R.string.labels_title));
            }
        } else {
            if (!mShowCheckboxes) {
                mTitle.setText(getString(titleRes));
            }
        }
    }

    public void onSaveClicked() {
        String labelName = mLabelName.getText().toString();
        if (TextUtils.isEmpty(labelName)) {
            TextExtensions.showToast(getActivity(), R.string.label_name_empty, Toast.LENGTH_SHORT);
            return;
        }
        for (LabelsAdapter.LabelItem item : mLabels){
            if (item.name.equals(labelName)){
                TextExtensions.showToast(getActivity(), R.string.label_name_duplicate, Toast.LENGTH_SHORT);
                return;
            }
        }

        mColorsGrid.setVisibility(View.GONE);
        mLabelName.setText("");
        mList.setVisibility(View.VISIBLE);
        UiUtil.hideKeyboard(getActivity(), mLabelName);
        if (mLabelCreationListener != null) {
            mLabelCreationListener.onLabelCreated(labelName, mSelectedNewLabelColor);
        }
        setDoneTitle(R.string.label_apply);
        setDialogTitle(R.string.labels_title_apply);
    }


    class LabelsObserver implements Observer<List<Label>>{

        @Override
        public void onChanged(@Nullable List<Label> labels) {
            mLabels = new ArrayList<>();
            if (labels == null) {
                labels = new ArrayList<>();
            }
            for (Label label : labels) {
                if (!label.getExclusive()) {
                    mLabels.add(fromLabel(label));
                }
            }
            if (isAdded()) {
                mAdapter = new LabelsAdapter(getActivity(), mLabels);
                mList.setAdapter(mAdapter);
                if (mLabels.size() == 0) {
                    mNoLabelsView.setVisibility(View.VISIBLE);
                    mListDivider.setVisibility(View.GONE);
                    mDone.setClickable(false);
                } else {
                    mNoLabelsView.setVisibility(View.GONE);
                    mListDivider.setVisibility(View.VISIBLE);
                    mDone.setClickable(true);
                }
            }
        }
    }

    private LabelsAdapter.LabelItem fromLabel(Label label) {
        LabelsAdapter.LabelItem labelItem =
                new LabelsAdapter.LabelItem(mCheckedLabels != null && mCheckedLabels.contains(label.getId()));
        int numberSelectedMessages = 0;
        if (mAllLabelsMap != null && mAllLabelsMap.containsKey(label.getId())) {
            numberSelectedMessages = mAllLabelsMap.get(label.getId());
        }
        if (mMessageIds == null || numberSelectedMessages == mMessageIds.size() || numberSelectedMessages == 0) {
            labelItem.states = 2;
        } else {
            labelItem.states = 3;
            labelItem.isUnchanged = true;
            labelItem.isAttached = false;
        }
        labelItem.labelId = label.getId();
        labelItem.name = label.getName();
        labelItem.color = label.getColor();
        labelItem.display = label.getDisplay();
        labelItem.order = label.getOrder();
        labelItem.numberOfSelectedMessages = numberSelectedMessages;

        return labelItem;
    }

    private List<String> getCheckedLabels() {
        List<String> checkedLabelIds = new ArrayList<>();
        List<LabelsAdapter.LabelItem> labelItems = mAdapter.getAllItems();
        for (LabelsAdapter.LabelItem item : labelItems) {
            if (item.isAttached) {
                checkedLabelIds.add(item.labelId);
            }
        }
        return checkedLabelIds;
    }

    private List<String> getUnchangedLabels() {
        List<String> unchangedLabelIds = new ArrayList<>();
        List<LabelsAdapter.LabelItem> labelItems = mAdapter.getAllItems();
        for (LabelsAdapter.LabelItem item : labelItems) {
            if (item.isUnchanged) {
                unchangedLabelIds.add(item.labelId);
            }
        }
        return unchangedLabelIds;
    }

    private void inflateColors() {
        mColorOptions = getResources().getIntArray(R.array.label_colors);
        mColorsAdapter = new LabelColorsAdapter(getActivity(), mColorOptions, R.layout.label_color_item);
        mColorsGrid.setAdapter(mColorsAdapter);
        mColorsGrid.setVisibility(View.VISIBLE);
        mList.setVisibility(View.GONE);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        int colorId = mColorOptions[position];
        mSelectedNewLabelColor = String.format("#%06X", 0xFFFFFF & colorId);
        mColorsAdapter.setChecked(position);
        UiUtil.hideKeyboard(getActivity(), mLabelName);
    }

    private void randomCheck() {
        if (mCurrentSelection == -1) {
            Random random = new Random();
            mCurrentSelection = random.nextInt(mColorOptions.length);
            int colorId = mColorOptions[mCurrentSelection];
            mSelectedNewLabelColor = String.format("#%06X", 0xFFFFFF & colorId);
            mColorsAdapter.setChecked(mCurrentSelection);
        }
    }

    public interface ILabelsChangeListener {
        void onLabelsChecked(List<String> checkedLabelIds, List<String> unchangedLabels, List<String> messageIds);
        void onLabelsChecked(List<String> checkedLabelIds, List<String> unchangedLabels, List<String> messageIds, List<String> messagesToArchive);
    }

    public interface ILabelCreationListener {
        void onLabelCreated(String labelName, String color);
        void onLabelsDeleted(List<String> checkedLabelIds);
    }
}
