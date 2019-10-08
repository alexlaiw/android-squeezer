package uk.org.ngo.squeezer.itemlist.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;

import uk.org.ngo.squeezer.framework.EnumWithTextAndIcon;
import uk.org.ngo.squeezer.framework.Item;
import uk.org.ngo.squeezer.menu.ViewMenuItemFragment;
import uk.org.ngo.squeezer.util.Reflection;

public abstract class BaseViewDialog<
        T extends Item,
        ListLayout extends Enum<ListLayout> & EnumWithTextAndIcon> extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressWarnings("unchecked") final Class<ListLayout> listLayoutClass = (Class<ListLayout>) Reflection.getGenericClass(getClass(), BaseViewDialog.class, 1);
        @SuppressWarnings("unchecked") final ViewMenuItemFragment.ListActivityWithViewMenu<T, ListLayout> activity = (ViewMenuItemFragment.ListActivityWithViewMenu<T, ListLayout>) getActivity();


        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getTitle());
        builder.setAdapter(new BaseAdapter() {
                               @Override
                               public boolean areAllItemsEnabled() {
                                   return false;
                               }

                               @Override
                               public boolean isEnabled(int position) {
                                   return true;
                               }

                               @Override
                               public int getCount() {
                                   return listLayoutClass.getEnumConstants().length;
                               }

                               @Override
                               public Object getItem(int i) {
                                   return null;
                               }

                               @Override
                               public long getItemId(int i) {
                                   return i;
                               }


                               @Override
                               public View getView(int position, View convertView,
                                                   ViewGroup parent) {
                                   CheckedTextView textView = (CheckedTextView) getActivity()
                                           .getLayoutInflater()
                                           .inflate(android.R.layout.select_dialog_singlechoice,
                                                   parent, false);
                                   ListLayout listLayout = listLayoutClass.getEnumConstants()[position];
                                   textView.setCompoundDrawablesWithIntrinsicBounds(
                                           getIcon(listLayout), 0, 0, 0);
                                   textView.setText(listLayout.getText(getActivity()));
                                   textView.setChecked(listLayout == activity.getListLayout());
                                   return textView;
                               }
                           }, new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialog, int position) {
                                   activity.setListLayout(listLayoutClass.getEnumConstants()[position]);
                                   dialog.dismiss();
                               }
                           }
        );
        return builder.create();
    }

    protected int getIcon(ListLayout listLayout) {
        TypedValue v = new TypedValue();
        getActivity().getTheme().resolveAttribute(listLayout.getIconAttribute(), v, true);
        return v.resourceId;
    }

    protected abstract String getTitle();

}
