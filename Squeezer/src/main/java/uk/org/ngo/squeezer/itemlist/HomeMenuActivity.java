/*
 * Copyright (c) 2009 Google Inc.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer.itemlist;


import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import uk.org.ngo.squeezer.Preferences;
import uk.org.ngo.squeezer.R;
import uk.org.ngo.squeezer.Squeezer;
import uk.org.ngo.squeezer.framework.ItemAdapter;
import uk.org.ngo.squeezer.itemlist.dialog.ArtworkListLayout;
import uk.org.ngo.squeezer.model.JiveItem;
import uk.org.ngo.squeezer.model.Window;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.event.HomeMenuEvent;

public class HomeMenuActivity extends JiveItemListActivity {

    private static final String TAG = "HomeMenuActivity";

    @Override
    protected void orderPage(@NonNull ISqueezeService service, int start) {
        // Do nothing we get the home menu from the sticky HomeMenuEvent
    }

    @Override
    public ArtworkListLayout getPreferredListLayout() {
        return new Preferences(this).getHomeMenuLayout();
    }

    @Override
    protected void saveListLayout(ArtworkListLayout listLayout) {
        new Preferences(this).setHomeMenuLayout(listLayout);
    }

    public void onEvent(HomeMenuEvent event) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (parent.window == null) {
                    applyWindowStyle(Window.WindowStyle.HOME_MENU);
                }
                if (parent != JiveItem.HOME && window.text == null) {
                    updateHeader(parent);
                }
                clearItemAdapter();
            }
        });
        List<JiveItem> menu = getMenuNode(parent.getId(), event.menuItems);
        onItemsReceived(menu.size(), 0, menu, JiveItem.class);
    }

    private List<JiveItem> getMenuNode(String node, List<JiveItem> homeMenu) {
        ArrayList<JiveItem> menu = new ArrayList<>();
        for (JiveItem item : homeMenu) {
            if (node.equals(item.getNode())) {
                menu.add(item);
            }
        }
        Collections.sort(menu, new Comparator<JiveItem>() {
            @Override
            public int compare(JiveItem o1, JiveItem o2) {
                if (o1.getWeight() == o2.getWeight()) {
                    return o1.getName().compareTo(o2.getName());
                }
                return o1.getWeight() - o2.getWeight();
            }
        });
        return menu;
    }

    public static void show(Activity activity, JiveItem item) {
        final Intent intent = new Intent(activity, HomeMenuActivity.class);
        intent.putExtra(JiveItem.class.getName(), item);
        activity.startActivity(intent);
    }

    @Override
    protected ItemAdapter<JiveItemView, JiveItem> createItemListAdapter() {

        return new ItemAdapter<JiveItemView, JiveItem>(this) {

            @Override
            public JiveItemView createViewHolder(View view) {
                return new JiveItemView(HomeMenuActivity.this, view) {
                    @Override
                    public void bindView(JiveItem item) {
                        super.bindView(item);
                        itemView.setOnLongClickListener(view -> {
                            if (!item.getId().equals(JiveItem.ARCHIVE.getId())) {
                                if (!item.getNode().equals(JiveItem.ARCHIVE.getId())) {
                                    if (getService().checkIfItemIsAlreadyInArchive(item)) {
                                        return true;
                                    }
                                }
                                removeItem(getAdapterPosition());
                                if (getService().toggleArchiveItem(item)) {
                                    HomeActivity.show(Squeezer.getContext());
                                };
                            }
                            else {
//                               TODO: Message to the User
                                 Log.d(TAG, "bindView: BEN - Archive can not be archived");
                            }
                            return true;
                        });
                    }
                };
            }

            @Override
            protected int getItemViewType(JiveItem item) {
                return item != null && item.hasSlider() ?
                        R.layout.slider_item : (getListLayout() == ArtworkListLayout.grid) ? R.layout.grid_item : R.layout.list_item;
            }
        };
    }

}
