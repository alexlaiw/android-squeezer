package uk.org.ngo.squeezer.menu;

import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.framework.EnumWithTextAndIcon;
import uk.org.ngo.squeezer.framework.Item;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.util.ThemeManager;

/**
 * A fragment that implements a "View" menu.
 * <p>
 * Activities that host this fragment must implement {@link ListActivityWithViewMenu}.
 * <p>
 * <pre>
 * {@code
 * public void onCreate(Bundle savedInstanceState) {
 *     ...
 *     BaseMenuFragment.add(this, OrderMenuItemFragment.class);
 * }
 *
 * public void showViewDialog() {
 * }
 * </pre>
 */
public class ViewMenuItemFragment extends BaseMenuFragment {

    private ListActivityWithViewMenu activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        activity = (ListActivityWithViewMenu) getActivity();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.viewmenuitem, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_view:
                activity.showViewDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Interface that activities that host this fragment must implement.
     */
    public interface ListActivityWithViewMenu<T extends Item,
            ListLayout extends Enum<ListLayout> & EnumWithTextAndIcon> {

        /**
         * Show a dialog allowing the user to choose the sort order.
         */
        void showViewDialog();

        /**
         * Ensure that the activity that hosts this fragment derives from FragmentActivity.
         */
        FragmentManager getSupportFragmentManager();

        ListLayout getListLayout();

        void setListLayout(ListLayout listLayout);

        /**
         * Ensure that the activity that hosts this fragment derives from BaseListActivity.
         */
        ItemAdapter<T> getItemAdapter();

        int getThemeId();

        void setTheme(ThemeManager.Theme theme);
    }
}
