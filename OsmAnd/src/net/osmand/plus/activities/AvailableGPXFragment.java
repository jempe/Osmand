package net.osmand.plus.activities;

import java.io.File;
import java.text.Collator;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.osmand.IndexConstants;
import net.osmand.access.AccessibleToast;
import net.osmand.data.LatLon;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuAdapter.OnContextMenuClick;
import net.osmand.plus.GPXUtilities;
import net.osmand.plus.GPXUtilities.GPXFile;
import net.osmand.plus.GPXUtilities.GPXTrackAnalysis;
import net.osmand.plus.GPXUtilities.WptPt;
import net.osmand.plus.GpxSelectionHelper;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.activities.search.SearchActivity;
import net.osmand.plus.dialogs.DirectionsDialogs;
import net.osmand.plus.download.LocalIndexesFragment;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.helpers.ScreenOrientationHelper;
import net.osmand.plus.monitoring.OsmandMonitoringPlugin;
import net.osmand.plus.osmedit.OsmEditingPlugin;
import net.osmand.plus.views.MonitoringInfoControl;
import net.osmand.util.Algorithms;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.SearchView;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


public class AvailableGPXFragment extends OsmandExpandableListFragment {


	public static final int SEARCH_ID = -1;
	//	public static final int ACTION_ID = 0;
//	protected static final int DELETE_ACTION_ID = 1;
	private boolean selectionMode = false;
	private List<GpxInfo> selectedItems = new ArrayList<>();
	private ActionMode actionMode;
	private LoadGpxTask asyncLoader;
	private GpxIndexesAdapter allGpxAdapter;
	private static MessageFormat formatMb = new MessageFormat("{0, number,##.#} MB", Locale.US);
	private LoadLocalIndexDescriptionTask descriptionLoader;
	private ContextMenuAdapter optionsMenuAdapter;
	private AsyncTask<GpxInfo, ?, ?> operationTask;
	private GpxSelectionHelper selectedGpxHelper;
	private SavingTrackHelper savingTrackHelper;
	private OsmandApplication app;
	private Drawable gpxNormal;
	private Drawable gpxOnMap;
	private boolean updateEnable;


	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		this.app = (OsmandApplication) getActivity().getApplication();
		final Collator collator = Collator.getInstance();
		collator.setStrength(Collator.SECONDARY);
		asyncLoader = new LoadGpxTask();
		selectedGpxHelper = ((OsmandApplication) activity.getApplication()).getSelectedGpxHelper();
		savingTrackHelper = ((OsmandApplication) activity.getApplication()).getSavingTrackHelper();
		allGpxAdapter = new GpxIndexesAdapter(getActivity());
		setAdapter(allGpxAdapter);
	}

	private void startHandler() {
		Handler updateCurrentRecordingTrack = new Handler();
		updateCurrentRecordingTrack.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (getView() != null && updateEnable) {
					updateCurrentTrack(getView(), getActivity(), app);
					startHandler();
				}
			}
		}, 2000);
	}

	public List<GpxInfo> getSelectedItems() {
		return selectedItems;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (asyncLoader == null || asyncLoader.getResult() == null) {
			asyncLoader = new LoadGpxTask();
			asyncLoader.execute(getActivity());
		} else {
			allGpxAdapter.refreshSelected();
			allGpxAdapter.notifyDataSetChanged();
		}

		updateCurrentTrack(getView(), getActivity(), app);
		updateEnable = true;
		startHandler();
	}


	@Override
	public void onPause() {
		super.onPause();
		updateEnable = false;
		if (operationTask != null) {
			operationTask.cancel(true);
		}
	}

	public static void updateCurrentTrack(View v,final Activity ctx, OsmandApplication app) {
		if (v == null) {
			return;
		}
		final boolean isRecording = app.getSettings().SAVE_GLOBAL_TRACK_TO_GPX.get();
		Drawable icon = app.getResources().getDrawable(isRecording ? R.drawable.ic_action_rec_stop : R.drawable.ic_play_dark).mutate();
		if (app.getSettings().isLightContent()) {
			icon.setColorFilter(app.getResources().getColor(R.color.icon_color_light), PorterDuff.Mode.MULTIPLY);
		}

		if (isRecording) {
			v.findViewById(R.id.show_on_map).setVisibility(View.VISIBLE);
		} else {
			v.findViewById(R.id.show_on_map).setVisibility(View.GONE);
		}
		ImageButton stop = ((ImageButton) v.findViewById(R.id.stop));
		stop.setImageDrawable(icon);
		stop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				final OsmandMonitoringPlugin plugin = OsmandPlugin.getEnabledPlugin(OsmandMonitoringPlugin.class);
				if (isRecording) {
					plugin.stopRecording();
				} else {
					plugin.startGPXMonitoring(ctx);
				}
			}
		});

//		if (isRecording) {
			SavingTrackHelper sth = app.getSavingTrackHelper();
			((TextView) v.findViewById(R.id.points_count)).setText(sth.getPoints()+"");
			((TextView) v.findViewById(R.id.distance)).setText(OsmAndFormatter.getFormattedDistance(
					sth.getDistance(), app));
			v.findViewById(R.id.points_count).setVisibility(View.VISIBLE);
			v.findViewById(R.id.points_icon).setVisibility(View.VISIBLE);
			v.findViewById(R.id.distance).setVisibility(View.VISIBLE);
			v.findViewById(R.id.distance_icon).setVisibility(View.VISIBLE);
//		} else {
//			v.findViewById(R.id.points_count).setVisibility(View.GONE);
//			v.findViewById(R.id.points_icon).setVisibility(View.GONE);
//			v.findViewById(R.id.distance).setVisibility(View.GONE);
//			v.findViewById(R.id.distance_icon).setVisibility(View.GONE);
//		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.available_gpx, container, false);
		listView = (ExpandableListView) v.findViewById(android.R.id.list);
		if (this.adapter != null) {
			listView.setAdapter(this.adapter);
		}
		setHasOptionsMenu(true);


		View currentTrackView = v.findViewById(R.id.current_track);
		createCurrentTrackView(v, getMyApplication());
		if (OsmandPlugin.getEnabledPlugin(OsmandMonitoringPlugin.class) == null) {
			currentTrackView.setVisibility(View.GONE);
		} else {
			currentTrackView.setVisibility(View.VISIBLE);
			currentTrackView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					MapActivity.launchMapActivityMoveToTop(getActivity());
				}
			});
		}
		gpxNormal = getResources().getDrawable(R.drawable.ic_gpx_track).mutate();
		gpxOnMap = getResources().getDrawable(R.drawable.ic_gpx_track).mutate();
		gpxOnMap.setColorFilter(getResources().getColor(R.color.color_distance), PorterDuff.Mode.MULTIPLY);
		if (getMyApplication().getSettings().isLightContent()) {
			gpxNormal.setColorFilter(getResources().getColor(R.color.icon_color_light), PorterDuff.Mode.MULTIPLY);
		}

		return v;
	}

	public static void createCurrentTrackView(View v, final OsmandApplication app) {
		((TextView) v.findViewById(R.id.name)).setText(R.string.currently_recording_track);
		v.findViewById(R.id.time_icon).setVisibility(View.GONE);
		boolean light = app.getSettings().isLightContent();

		Drawable icon = app.getResources().getDrawable(R.drawable.ic_action_gsave_dark).mutate();
		if (light) {
			icon.setColorFilter(app.getResources().getColor(R.color.icon_color_light), PorterDuff.Mode.MULTIPLY);
		}
		ImageButton save = ((ImageButton) v.findViewById(R.id.show_on_map));
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

			}
		});
		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Runnable run = new Runnable() {
					@Override
					public void run() {
						final OsmandMonitoringPlugin plugin = OsmandPlugin.getEnabledPlugin(OsmandMonitoringPlugin.class);
						plugin.saveCurrentTrack();
					}
				};
				run.run();
			}
		});
		v.findViewById(R.id.divider).setVisibility(View.GONE);

		v.findViewById(R.id.options).setVisibility(View.GONE);
		v.findViewById(R.id.stop).setVisibility(View.VISIBLE);
		v.findViewById(R.id.check_item).setVisibility(View.GONE);
		save.setImageDrawable(icon);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.clear();
		MenuItem mi = createMenuItem(menu, SEARCH_ID, R.string.search_poi_filter, R.drawable.ic_action_search_dark,
				R.drawable.ic_action_search_dark, MenuItemCompat.SHOW_AS_ACTION_ALWAYS
						| MenuItemCompat.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		SearchView searchView = new SearchView(getActivity());
		FavoritesActivity.updateSearchView(getActivity(), searchView);
		MenuItemCompat.setActionView(mi, searchView);
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				allGpxAdapter.getFilter().filter(query);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				allGpxAdapter.getFilter().filter(newText);
				return true;
			}
		});
		MenuItemCompat.setOnActionExpandListener(mi, new MenuItemCompat.OnActionExpandListener() {
			@Override
			public boolean onMenuItemActionExpand(MenuItem item) {
				return true;
			}

			@Override
			public boolean onMenuItemActionCollapse(MenuItem item) {
				// Needed to hide intermediate progress bar after closing action mode
				new Handler().postDelayed(new Runnable() {
					public void run() {
						hideProgressBar();
					}
				}, 100);
				return true;
			}
		});

		if (ScreenOrientationHelper.isOrientationPortrait(getActivity())) {
			menu = ((FavoritesActivity) getActivity()).getClearToolbar(true).getMenu();
		} else {
			((FavoritesActivity) getActivity()).getClearToolbar(false);
		}


		optionsMenuAdapter = new ContextMenuAdapter(getActivity());
		OnContextMenuClick listener = new OnContextMenuClick() {
			@Override
			public boolean onContextMenuClick(ArrayAdapter<?> adapter, final int itemId, int pos, boolean isChecked) {
				if (itemId == R.string.local_index_mi_reload) {
					asyncLoader = new LoadGpxTask();
					asyncLoader.execute(getActivity());
				} else if (itemId == R.string.show_gpx_route) {
					openShowOnMapMode();
				} else if (itemId == R.string.local_index_mi_delete) {
					openSelectionMode(itemId, R.drawable.ic_action_delete_dark, R.drawable.ic_action_delete_dark,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog, int which) {
									doAction(itemId);
								}
							});
				}
				return true;
			}
		};
		optionsMenuAdapter.item(R.string.show_gpx_route)
				.icons(R.drawable.ic_show_on_map, R.drawable.ic_show_on_map).listen(listener).reg();
		optionsMenuAdapter.item(R.string.local_index_mi_delete)
				.icons(R.drawable.ic_action_delete_dark, R.drawable.ic_action_delete_dark).listen(listener).reg();
		optionsMenuAdapter.item(R.string.local_index_mi_reload)
				.icons(R.drawable.ic_action_refresh_dark, R.drawable.ic_action_refresh_dark).listen(listener).reg();
		OsmandPlugin.onOptionsMenuActivity(getActivity(), this, optionsMenuAdapter);
		for (int j = 0; j < optionsMenuAdapter.length(); j++) {
			final MenuItem item;
			item = menu.add(0, optionsMenuAdapter.getElementId(j), j + 1, optionsMenuAdapter.getItemName(j));
			MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			if (ScreenOrientationHelper.isOrientationPortrait(getActivity())) {
				item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(MenuItem menuItem) {
						onOptionsItemSelected(item);
						return true;
					}
				});
			}
			if (optionsMenuAdapter.getImageId(j, isLightActionBar()) != 0) {
				item.setIcon(optionsMenuAdapter.getImageId(j, false));
			}

		}
	}


	public void doAction(int actionResId) {
		if (actionResId == R.string.local_index_mi_delete) {
			operationTask = new DeleteGpxTask();
			operationTask.execute(selectedItems.toArray(new GpxInfo[selectedItems.size()]));
		} else {
			operationTask = null;
		}
		if (actionMode != null) {
			actionMode.finish();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		for (int i = 0; i < optionsMenuAdapter.length(); i++) {
			if (itemId == optionsMenuAdapter.getElementId(i)) {
				optionsMenuAdapter.getClickAdapter(i).onContextMenuClick(null, itemId, i, false);
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	public void showProgressBar() {
		((FavoritesActivity) getActivity()).setSupportProgressBarIndeterminateVisibility(true);
	}

	public void hideProgressBar() {
		if (getActivity() != null) {
			((FavoritesActivity) getActivity()).setSupportProgressBarIndeterminateVisibility(false);
		}
	}

	private void updateSelectionMode(ActionMode m) {
		if (selectedItems.size() > 0) {
			m.setTitle(selectedItems.size() + " " + app.getString(R.string.selected));
		} else {
			m.setTitle("");
		}
	}

	private void enableSelectionMode(boolean selectionMode) {
		this.selectionMode = selectionMode;
		if (ScreenOrientationHelper.isOrientationPortrait(getActivity())) {
			((FavoritesActivity) getActivity()).setToolbarVisibility(!selectionMode);
		}
	}

	private void openShowOnMapMode() {
		enableSelectionMode(true);
		selectedItems.clear();
		final Set<GpxInfo> originalSelectedItems = allGpxAdapter.getSelectedGpx();
		selectedItems.addAll(originalSelectedItems);
		actionMode = getActionBarActivity().startSupportActionMode(new ActionMode.Callback() {

			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				enableSelectionMode(true);
				updateSelectionMode(mode);
				MenuItem it = menu.add(R.string.show_gpx_route);
				it.setIcon(R.drawable.ic_action_done); 
				MenuItemCompat.setShowAsAction(it, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
				return true;
			}


			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return false;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				runSelection(false);
				actionMode.finish();
				allGpxAdapter.refreshSelected();
				allGpxAdapter.notifyDataSetChanged();
				return true;
			}

			private void runSelection(boolean showOnMap) {
				operationTask = new SelectGpxTask(showOnMap);
				originalSelectedItems.addAll(selectedItems);
				operationTask.execute(originalSelectedItems.toArray(new GpxInfo[originalSelectedItems.size()]));
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				enableSelectionMode(false);
				runSelection(false);
				allGpxAdapter.notifyDataSetChanged();
			}

		});
		allGpxAdapter.notifyDataSetChanged();
	}


	public void openSelectionMode(final int actionResId, int darkIcon, int lightIcon,
								  final DialogInterface.OnClickListener listener) {
		final int actionIconId = !isLightActionBar() ? darkIcon : lightIcon;
		String value = app.getString(actionResId);
		if (value.endsWith("...")) {
			value = value.substring(0, value.length() - 3);
		}
		final String actionButton = value;
		if (allGpxAdapter.getGroupCount() == 0) {
			AccessibleToast.makeText(getActivity(), app.getString(R.string.local_index_no_items_to_do, actionButton.toLowerCase()), Toast.LENGTH_SHORT).show();
			return;
		}

		enableSelectionMode(true);
		selectedItems.clear();
		actionMode = getActionBarActivity().startSupportActionMode(new ActionMode.Callback() {

			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				enableSelectionMode(true);
				MenuItem it = menu.add(actionResId);
				if (actionIconId != 0) {
					it.setIcon(actionIconId);
				}
				MenuItemCompat.setShowAsAction(it, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
				return true;
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return false;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				if (selectedItems.isEmpty()) {
					AccessibleToast.makeText(getActivity(),
							app.getString(R.string.local_index_no_items_to_do, actionButton.toLowerCase()), Toast.LENGTH_SHORT).show();
					return true;
				}

				Builder builder = new AlertDialog.Builder(getActivity());
				builder.setMessage(getString(R.string.local_index_action_do, actionButton.toLowerCase(), selectedItems.size()));
				builder.setPositiveButton(actionButton, listener);
				builder.setNegativeButton(R.string.default_buttons_cancel, null);
				builder.show();
				return true;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				enableSelectionMode(false);
				allGpxAdapter.notifyDataSetChanged();
			}

		});
		allGpxAdapter.notifyDataSetChanged();
	}

	private void renameFile(GpxInfo info) {
		final File f = info.file;
		Builder b = new AlertDialog.Builder(getActivity());
		if (f.exists()) {
			final EditText editText = new EditText(getActivity());
			editText.setPadding(7, 3, 7, 3);
			editText.setText(f.getName());
			b.setView(editText);
			b.setPositiveButton(R.string.default_buttons_save, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					String newName = editText.getText().toString();
					File dest = new File(f.getParentFile(), newName);
					if (dest.exists()) {
						AccessibleToast.makeText(getActivity(), R.string.file_with_name_already_exists, Toast.LENGTH_LONG).show();
					} else {
						if (!f.getParentFile().exists()) {
							f.getParentFile().mkdirs();
						}
						if (f.renameTo(dest)) {
							asyncLoader = new LoadGpxTask();
							asyncLoader.execute(getActivity());
						} else {
							AccessibleToast.makeText(getActivity(), R.string.file_can_not_be_renamed, Toast.LENGTH_LONG).show();
						}
					}

				}
			});
			b.setNegativeButton(R.string.default_buttons_cancel, null);
			b.show();
		}
	}


	private void showGpxOnMap(GpxInfo info) {
		info.updateGpxInfo(getMyApplication());
		boolean e = true;
		if (info != null && info.gpx != null) {
			WptPt loc = info.gpx.findPointToShow();
			OsmandSettings settings = getMyApplication().getSettings();
			if (loc != null) {
				settings.setMapLocationToShow(loc.lat, loc.lon, settings.getLastKnownMapZoom());
				e = false;
				getMyApplication().getSelectedGpxHelper().setGpxFileToDisplay(info.gpx);
				MapActivity.launchMapActivityMoveToTop(getActivity());
			}
		}
		if (e) {
			AccessibleToast.makeText(getActivity(), R.string.gpx_file_is_empty, Toast.LENGTH_LONG).show();
		}
	}

	public class LoadGpxTask extends AsyncTask<Activity, GpxInfo, List<GpxInfo>> {

		private List<GpxInfo> result;

		@Override
		protected List<GpxInfo> doInBackground(Activity... params) {
			List<GpxInfo> result = new ArrayList<GpxInfo>();
			if (!savingTrackHelper.getCurrentGpx().isEmpty()) {
				loadFile(new GpxInfo(savingTrackHelper.getCurrentGpx(),
						app.getString(R.string.gpx_available_current_track)));
			}
			loadGPXData(app.getAppPath(IndexConstants.GPX_INDEX_DIR), result, this);
			return result;
		}

		public void loadFile(GpxInfo... loaded) {
			publishProgress(loaded);
		}

		@Override
		protected void onPreExecute() {
			((ActionBarActivity) getActivity()).setSupportProgressBarIndeterminateVisibility(true);
			allGpxAdapter.clear();
		}

		@Override
		protected void onProgressUpdate(GpxInfo... values) {
			for (GpxInfo v : values) {
				allGpxAdapter.addLocalIndexInfo(v);
			}
			allGpxAdapter.notifyDataSetChanged();
		}

		public void setResult(List<GpxInfo> result) {
			this.result = result;
			allGpxAdapter.clear();
			if (result != null) {
				for (GpxInfo v : result) {
					allGpxAdapter.addLocalIndexInfo(v);
				}
				allGpxAdapter.refreshSelected();
				allGpxAdapter.notifyDataSetChanged();
				onPostExecute(result);
			}
		}

		@Override
		protected void onPostExecute(List<GpxInfo> result) {
			this.result = result;
			allGpxAdapter.refreshSelected();
			if (getActivity() != null) {
				((ActionBarActivity) getActivity()).setSupportProgressBarIndeterminateVisibility(false);
			}
			if (allGpxAdapter.getGroupCount() > 0) {
				getExpandableListView().expandGroup(0);
			}
		}

		private File[] listFilesSorted(File dir) {
			File[] listFiles = dir.listFiles();
			if (listFiles == null) {
				return new File[0];
			}
			Arrays.sort(listFiles);
			return listFiles;
		}

		private void loadGPXData(File mapPath, List<GpxInfo> result, LoadGpxTask loadTask) {
			if (mapPath.canRead()) {
				List<GpxInfo> progress = new ArrayList<>();
				loadGPXFolder(mapPath, result, loadTask, progress, "");
				if (!progress.isEmpty()) {
					loadTask.loadFile(progress.toArray(new GpxInfo[progress.size()]));
				}
			}
		}

		private void loadGPXFolder(File mapPath, List<GpxInfo> result, LoadGpxTask loadTask,
								   List<GpxInfo> progress, String gpxSubfolder) {
			for (File gpxFile : listFilesSorted(mapPath)) {
				if (gpxFile.isDirectory()) {
					String sub = gpxSubfolder.length() == 0 ? gpxFile.getName() : gpxSubfolder + "/" + gpxFile.getName();
					loadGPXFolder(gpxFile, result, loadTask, progress, sub);
				} else if (gpxFile.isFile() && gpxFile.getName().endsWith(".gpx")) {
					GpxInfo info = new GpxInfo();
					info.subfolder = gpxSubfolder;
					info.file = gpxFile;
					result.add(info);
					progress.add(info);
					if (progress.size() > 7) {
						loadTask.loadFile(progress.toArray(new GpxInfo[progress.size()]));
						progress.clear();
					}

				}
			}
		}

		public List<GpxInfo> getResult() {
			return result;
		}

	}


	protected class GpxIndexesAdapter extends OsmandBaseExpandableListAdapter implements Filterable {

		Map<String, List<GpxInfo>> data = new LinkedHashMap<>();
		List<String> category = new ArrayList<>();
		List<GpxInfo> selected = new ArrayList<>();
		int warningColor;
		int defaultColor;
		int corruptedColor;
		private SearchFilter filter;


		public GpxIndexesAdapter(Context ctx) {
			warningColor = ctx.getResources().getColor(R.color.color_warning);
			TypedArray ta = ctx.getTheme().obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
			defaultColor = ta.getColor(0, ctx.getResources().getColor(R.color.color_unknown));
			ta.recycle();
		}

		public void refreshSelected() {
			selected.clear();
			selected.addAll(getSelectedGpx());			
		}

		public Set<GpxInfo> getSelectedGpx() {
			Set<GpxInfo> originalSelectedItems = new HashSet<>();
			for (List<GpxInfo> l : data.values()) {
				if (l != null) {
					for (GpxInfo g : l) {
						boolean add;
						if (g.gpx != null && g.gpx.showCurrentTrack) {
							add = selectedGpxHelper.getSelectedCurrentRecordingTrack() != null;
						} else {
							add = selectedGpxHelper.getSelectedFileByName(g.getFileName()) != null;
						}
						if (add) {
							originalSelectedItems.add(g);
						}
					}
				}
			}
			return originalSelectedItems;
		}

		public void clear() {
			data.clear();
			category.clear();
			selected.clear();
			notifyDataSetChanged();
		}

		public void addLocalIndexInfo(GpxInfo info) {
			String catName;
			if (info.gpx != null && info.gpx.showCurrentTrack) {
				catName = info.name;
			} else {
				//local_indexes_cat_gpx now obsolete in new UI screen which shows only GPX data
				//catName = app.getString(R.string.local_indexes_cat_gpx) + " " + info.subfolder;
				catName = "" + info.subfolder;
			}
			int found = -1;
			// search from end
			for (int i = category.size() - 1; i >= 0; i--) {
				String cat = category.get(i);
				if (Algorithms.objectEquals(catName, cat)) {
					found = i;
					break;
				}
			}
			if (found == -1) {
				found = category.size();
				category.add(catName);
			}
			if (!data.containsKey(category.get(found))) {
				data.put(category.get(found), new ArrayList<GpxInfo>());
			}
			data.get(category.get(found)).add(info);
		}

		@Override
		public GpxInfo getChild(int groupPosition, int childPosition) {
			if(isSelectedGroup(groupPosition)) {
				return selected.get(childPosition);
			}
			String cat = category.get(getGroupPosition(groupPosition));
			return data.get(cat).get(childPosition);
		}

		@Override
		public long getChildId(int groupPosition, int childPosition) {
			// it would be unusable to have 10000 local indexes
			return groupPosition * 10000 + childPosition;
		}

		@Override
		public View getChildView(final int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
			View v = convertView;
			final GpxInfo child = getChild(groupPosition, childPosition);
			if (v == null) {
				LayoutInflater inflater = getActivity().getLayoutInflater();
				v = inflater.inflate(R.layout.dash_gpx_track_item, parent, false);
			}
			udpateGpxInfoView(v, child, app, gpxNormal, gpxOnMap, false);

			ImageView icon = (ImageView) v.findViewById(R.id.icon);
			ImageButton options = (ImageButton) v.findViewById(R.id.options);
			options.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					openPopUpMenu(v, child);
				}
			});


			final CheckBox checkbox = (CheckBox) v.findViewById(R.id.check_local_index);
			checkbox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
			if (selectionMode) {
				checkbox.setChecked(selectedItems.contains(child));
				checkbox.setOnClickListener(new View.OnClickListener() {


					@Override
					public void onClick(View v) {
						if (checkbox.isChecked()) {
							selectedItems.add(child);
						} else {
							selectedItems.remove(child);
						}
						updateSelectionMode(actionMode);
					}
				});
				icon.setVisibility(View.GONE);
				options.setVisibility(View.GONE);
			} else {
				icon.setVisibility(View.VISIBLE);
				options.setVisibility(View.VISIBLE);
			}

			final CompoundButton checkItem = (CompoundButton) v.findViewById(R.id.check_item);
			if (isSelectedGroup(groupPosition)) {
				checkItem.setVisibility(View.VISIBLE);
				v.findViewById(R.id.options).setVisibility(View.GONE);
			} else {
				checkItem.setVisibility(View.GONE);
			}

			final SelectedGpxFile selectedGpxFile = selectedGpxHelper.getSelectedFileByName(child.getFileName());
			checkItem.setChecked(selectedGpxFile != null);
			checkItem.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (child.gpx != null) {
						selectedGpxHelper.selectGpxFile(child.gpx, checkItem.isChecked(), false);
					} else {
						selectedGpxHelper.getSelectedGPXFiles().remove(selectedGpxFile);
					}
				}
			});

			v.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					onChildClick(null, v, groupPosition, childPosition, 0);
				}
			});
			return v;
		}


		@Override
		public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			View v = convertView;
			String group = getGroup(groupPosition);
			if (v == null) {
				LayoutInflater inflater = getActivity().getLayoutInflater();
				v = inflater.inflate(net.osmand.plus.R.layout.expandable_list_item_category, parent, false);
			}
			StringBuilder t = new StringBuilder(group);
			adjustIndicator(groupPosition, isExpanded, v, getMyApplication().getSettings().isLightContent());
			TextView nameView = ((TextView) v.findViewById(R.id.category_name));
			List<GpxInfo> list = isSelectedGroup(groupPosition)? selected : data.get(group);
			int size = 0;
			for (int i = 0; i < list.size(); i++) {
				int sz = list.get(i).getSize();
				if (sz < 0) {
					size = 0;
					break;
				} else {
					size += sz;
				}
			}
			size = size / (1 << 10);
			if (size > 0) {
				t.append(" [").append(size).append(" MB]");
			}
			nameView.setText(t.toString());
			nameView.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

			return v;
		}

		@Override
		public int getChildrenCount(int groupPosition) {
			if(isSelectedGroup(groupPosition)) {
				return selected.size();
			}
			return data.get(category.get(getGroupPosition(groupPosition))).size();
		}

		private int getGroupPosition(int groupPosition) {
			return isShowingSelection() ? groupPosition - 1 : groupPosition;
		}

		private boolean isSelectedGroup(int groupPosition) {
			return isShowingSelection() && groupPosition == 0;
		}

		private boolean isShowingSelection() {
			return selected.size() > 0 && !selectionMode;
		}

		@Override
		public String getGroup(int groupPosition) {
			if(isSelectedGroup(groupPosition)) {
				return app.getString(R.string.selected_tracks);
			}
			return category.get(getGroupPosition(groupPosition));
		}

		@Override
		public int getGroupCount() {
			if(isShowingSelection()) {
				return category.size() + 1;
			}
			return category.size();
		}

		@Override
		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return true;
		}

		@Override
		public Filter getFilter() {
			if (filter == null) {
				filter = new SearchFilter();
			}
			return filter;
		}

		public void delete(GpxInfo g) {
			int found = -1;
			// search from end
			for (int i = category.size() - 1; i >= 0; i--) {
				String cat = category.get(i);
				//local_indexes_cat_gpx now obsolete in new UI screen which shows only GPX data
				//if (Algorithms.objectEquals(getActivity().getString(R.string.local_indexes_cat_gpx) + " " + g.subfolder, cat)) {
				if (Algorithms.objectEquals("" + g.subfolder, cat)) {
					found = i;
					break;
				}
			}
			if (found != -1) {
				data.get(category.get(found)).remove(g);
			}
		}
	}

	private void openPopUpMenu(View v, final GpxInfo gpxInfo) {
		boolean light = getMyApplication().getSettings().isLightContent();
		final PopupMenu optionsMenu = new PopupMenu(getActivity(), v);
		DirectionsDialogs.setupPopUpMenuIcon(optionsMenu);
		Drawable showIcon = getResources().getDrawable(R.drawable.ic_show_on_map);
		if (light) {
			showIcon = showIcon.mutate();
			showIcon.setColorFilter(getResources().getColor(R.color.icon_color_light), PorterDuff.Mode.MULTIPLY);
		}
		MenuItem item = optionsMenu.getMenu().add(R.string.show_gpx_route)
				.setIcon(showIcon);
		item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				showGpxOnMap(gpxInfo);
				return true;
			}
		});

		item = optionsMenu.getMenu().add(R.string.local_index_mi_rename)
				.setIcon(light ? R.drawable.ic_action_edit_light : R.drawable.ic_action_edit_dark);
		item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				LocalIndexesFragment.renameFile(getActivity(), gpxInfo.file, new Runnable() {
					
					@Override
					public void run() {
						asyncLoader = new LoadGpxTask();
						asyncLoader.execute(getActivity());						
					}
				});
				return true;
			}
		});
		item = optionsMenu.getMenu().add(R.string.share_fav)
				.setIcon(light ? R.drawable.ic_action_gshare_light : R.drawable.ic_action_gshare_dark);
		item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				final Uri fileUri = Uri.fromFile(gpxInfo.file);
				final Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
				sendIntent.setType("application/gpx+xml");
				startActivity(sendIntent);
				return true;
			}
		});

		final OsmEditingPlugin osmEditingPlugin = OsmandPlugin.getEnabledPlugin(OsmEditingPlugin.class);
		if (osmEditingPlugin != null && osmEditingPlugin.isActive()) {
			Drawable exportIcon = getResources().getDrawable(R.drawable.ic_action_export);
			if (light) {
				exportIcon = exportIcon.mutate();
				exportIcon.setColorFilter(getResources().getColor(R.color.icon_color_light), PorterDuff.Mode.MULTIPLY);
			}			
			item = optionsMenu.getMenu().add(R.string.export)
					.setIcon(exportIcon);
			item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
				@Override
				public boolean onMenuItemClick(MenuItem item) {
					osmEditingPlugin.sendGPXFiles(getActivity(), AvailableGPXFragment.this, gpxInfo);
					return true;
				}
			});

		}

		item = optionsMenu.getMenu().add(R.string.edit_filter_delete_menu_item)
				.setIcon(light ? R.drawable.ic_action_delete_light : R.drawable.ic_action_delete_dark);
		item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
				builder.setMessage(R.string.recording_delete_confirm);
				builder.setPositiveButton(R.string.default_buttons_yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						operationTask = new DeleteGpxTask();
						operationTask.execute(gpxInfo);
					}
				});
				builder.setNegativeButton(R.string.default_buttons_cancel, null);
				builder.show();
				return true;
			}
		});
		optionsMenu.show();

	}

	public class LoadLocalIndexDescriptionTask extends AsyncTask<GpxInfo, GpxInfo, GpxInfo[]> {

		@Override
		protected GpxInfo[] doInBackground(GpxInfo... params) {
			for (GpxInfo i : params) {
				i.updateGpxInfo(getMyApplication());
			}
			return params;
		}

		@Override
		protected void onPreExecute() {
			showProgressBar();
		}

		@Override
		protected void onProgressUpdate(GpxInfo... values) {
			allGpxAdapter.notifyDataSetChanged();
		}

		@Override
		protected void onPostExecute(GpxInfo[] result) {
			hideProgressBar();
			allGpxAdapter.notifyDataSetChanged();

		}

	}

	public class DeleteGpxTask extends AsyncTask<GpxInfo, GpxInfo, String> {

		@Override
		protected String doInBackground(GpxInfo... params) {
			int count = 0;
			int total = 0;
			for (GpxInfo info : params) {
				if (!isCancelled() && (info.gpx == null || !info.gpx.showCurrentTrack)) {
					boolean successfull;
					successfull = Algorithms.removeAllFiles(info.file);
					total++;
					if (successfull) {
						count++;
						publishProgress(info);
					}
				}
			}
			return app.getString(R.string.local_index_items_deleted, count, total);
		}


		@Override
		protected void onProgressUpdate(GpxInfo... values) {
			for (GpxInfo g : values) {
				allGpxAdapter.delete(g);
			}
			allGpxAdapter.notifyDataSetChanged();
		}

		@Override
		protected void onPreExecute() {
			getActivity().setProgressBarIndeterminateVisibility(true);
		}

		@Override
		protected void onPostExecute(String result) {
			getActivity().setProgressBarIndeterminateVisibility(false);
			AccessibleToast.makeText(getActivity(), result, Toast.LENGTH_LONG).show();
		}
	}

	public class SelectGpxTask extends AsyncTask<GpxInfo, GpxInfo, String> {

		private boolean showOnMap;
		private WptPt toShow;

		public SelectGpxTask(boolean showOnMap) {
			this.showOnMap = showOnMap;
		}


		@Override
		protected String doInBackground(GpxInfo... params) {
			for (GpxInfo info : params) {
				if (!isCancelled()) {
					info.updateGpxInfo(getMyApplication());
					publishProgress(info);
				}
			}
			return "";
		}


		@Override
		protected void onProgressUpdate(GpxInfo... values) {
			for (GpxInfo g : values) {
				final boolean visible = selectedItems.contains(g);
				selectedGpxHelper.selectGpxFile(g.gpx, visible, false);
				if (visible && toShow == null) {
					toShow = g.gpx.findPointToShow();
				}
			}
			allGpxAdapter.notifyDataSetInvalidated();
		}

		@Override
		protected void onPreExecute() {
			getActivity().setProgressBarIndeterminateVisibility(true);
		}

		@Override
		protected void onPostExecute(String result) {
			getActivity().setProgressBarIndeterminateVisibility(false);
			allGpxAdapter.refreshSelected();
			allGpxAdapter.notifyDataSetChanged();
			if (showOnMap && toShow != null) {
				getMyApplication().getSettings().setMapLocationToShow(toShow.lat, toShow.lon,
						getMyApplication().getSettings().getLastKnownMapZoom());
				MapActivity.launchMapActivityMoveToTop(getActivity());
			}
		}
	}

	private void loadGpxAsync(GpxInfo info, boolean isSelected) {
		final boolean selected = isSelected;
		new AsyncTask<GpxInfo, Void, Void>() {
			GpxInfo info;

			@Override
			protected Void doInBackground(GpxInfo... params) {
				if (params == null) {
					return null;
				}
				info = params[0];
				params[0].updateGpxInfo(getMyApplication());
				return null;
			}


			@Override
			protected void onProgressUpdate(Void... values) {
			}

			@Override
			protected void onPreExecute() {
				getActivity().setProgressBarIndeterminateVisibility(true);
			}

			@Override
			protected void onPostExecute(Void result) {
				if (getActivity() != null) {
					getActivity().setProgressBarIndeterminateVisibility(false);
				}
				if (info.gpx != null) {
					getMyApplication().getSelectedGpxHelper().selectGpxFile(info.gpx, selected, true);
					allGpxAdapter.notifyDataSetChanged();
				}
			}
		}.execute(info);
	}


	private class SearchFilter extends Filter {


		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			FilterResults results = new FilterResults();
			final List<GpxInfo> raw = asyncLoader.getResult();
			if (constraint == null || constraint.length() == 0 || raw == null) {
				results.values = raw;
				results.count = 1;
			} else {
				String cs = constraint.toString().toLowerCase();
				List<GpxInfo> res = new ArrayList<>();
				for (GpxInfo r : raw) {
					if (r.getName().toLowerCase().indexOf(cs) != -1) {
						res.add(r);
					}
				}
				results.values = res;
				results.count = res.size();
			}
			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			if (results.values != null) {
				synchronized (allGpxAdapter) {
					allGpxAdapter.clear();
					for (GpxInfo i : ((List<GpxInfo>) results.values)) {
						allGpxAdapter.addLocalIndexInfo(i);
					}
					allGpxAdapter.refreshSelected();
				}
				allGpxAdapter.notifyDataSetChanged();
				if (constraint != null && constraint.length() > 3) {
					collapseTrees(10);
				}
			}
		}

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (descriptionLoader != null) {
			descriptionLoader.cancel(true);
		}
		if (asyncLoader != null) {
			asyncLoader.cancel(true);
		}
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
		GpxInfo item = allGpxAdapter.getChild(groupPosition, childPosition);
		
		
		if (!selectionMode) {
			Intent newIntent = new Intent(getActivity(), getMyApplication().getAppCustomization().getTrackActivity());
			// causes wrong position caching:  newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			newIntent.putExtra(TrackActivity.TRACK_FILE_NAME, item.file.getAbsolutePath());
			newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(newIntent);
//			item.setExpanded(!item.isExpanded());
//			if (item.isExpanded()) {
//				descriptionLoader = new LoadLocalIndexDescriptionTask();
//				descriptionLoader.execute(item);
//			}
		} else {
			if (!selectedItems.contains(item)) {
				selectedItems.add(item);
			} else {
				selectedItems.remove(item);
			}
			updateSelectionMode(actionMode);
		}
		allGpxAdapter.notifyDataSetInvalidated();
		return true;
	}


	public static class GpxInfo {
		public GPXFile gpx;
		public File file;
		public String subfolder;

		private String name = null;
		private int sz = -1;
		private String fileName = null;
		private String description;
		private boolean corrupted;
		private boolean expanded;
		private Spanned htmlDescription;
		private GPXUtilities.GPXTrackAnalysis analysis;

		public GpxInfo() {
		}

		public GpxInfo(GPXFile file, String name) {
			this.gpx = file;
			this.name = name;
		}

		public String getName() {
			if (name == null) {
				name = formatName(file.getName());
			}
			return name;
		}

		private String formatName(String name) {
			int ext = name.lastIndexOf('.');
			if (ext != -1) {
				name = name.substring(0, ext);
			}
			return name.replace('_', ' ');
		}

		public boolean isCorrupted() {
			return corrupted;
		}

		public int getSize() {
			if (sz == -1) {
				if (file == null) {
					return -1;
				}
				sz = (int) (file.length() >> 10);
			}
			return sz;
		}

		public boolean isExpanded() {
			return expanded;
		}

		public void setExpanded(boolean expanded) {
			this.expanded = expanded;
		}

		public CharSequence getDescription() {
			if (description == null) {
				return "";
			}
			return description;
		}

		public long getFileDate() {
			if (file == null) {
				return 0;
			}
			return file.lastModified();
		}

		public Spanned getHtmlDescription() {
			if (htmlDescription != null) {
				return htmlDescription;
			}
			htmlDescription = Html.fromHtml(getDescription().toString().replace("\n", "<br/>"));
			return htmlDescription;
		}

		public GPXUtilities.GPXTrackAnalysis getAnalysis() {
			return analysis;
		}

		public void setAnalysis(GPXUtilities.GPXTrackAnalysis analysis) {
			this.analysis = analysis;
		}

		public void setGpx(GPXFile gpx) {
			this.gpx = gpx;
		}

		public void updateGpxInfo(OsmandApplication app) {
			if (gpx == null) {
				gpx = GPXUtilities.loadGPXFile(app, file);
			}
			if (gpx.warning != null) {
				corrupted = true;
				description = gpx.warning;
				analysis = null;
			} else {
				// 'Long-press for options' message
				analysis = gpx.getAnalysis(file.lastModified());
				description = GpxUiHelper.getDescription(app, analysis, true);
			}
			htmlDescription = null;
			getHtmlDescription();
		}

		public String getFileName() {
			if (fileName != null) {
				return fileName;
			}
			if (file == null) {
				return "";
			}
			return fileName = file.getName();
		}
	}

	public static void udpateGpxInfoView(View v, GpxInfo child, OsmandApplication app,
										 Drawable gpxNormal, Drawable gpxOnMap,
										 boolean isDashItem) {
		TextView viewName = ((TextView) v.findViewById(R.id.name));
		if (!isDashItem) {
			v.findViewById(R.id.divider).setVisibility(View.GONE);
		} else {
			v.findViewById(R.id.divider).setVisibility(View.VISIBLE);
		}


		viewName.setText(child.getName());
		GpxSelectionHelper selectedGpxHelper = app.getSelectedGpxHelper();

		//ImageView icon = (ImageView) v.findViewById(!isDashItem? R.id.icon : R.id.show_on_map);
		ImageView icon = (ImageView) v.findViewById(R.id.icon);
		icon.setVisibility(View.VISIBLE);
		icon.setImageDrawable(gpxNormal);
		if (child.isCorrupted()) {
			viewName.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
		} else {
			viewName.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
		}
		SelectedGpxFile sgpx = selectedGpxHelper.getSelectedFileByName(child.getFileName());
		GPXTrackAnalysis analysis = null;
		if (sgpx != null) {
			icon.setImageDrawable(gpxOnMap);
			analysis = sgpx.getTrackAnalysis();

		}
		boolean sectionRead = analysis == null;
		if (sectionRead) {
			v.findViewById(R.id.read_section).setVisibility(View.GONE);
			v.findViewById(R.id.unknown_section).setVisibility(View.VISIBLE);
			String date = "";
			String size = "";
			if (child.getSize() >= 0) {
				if (child.getSize() > 100) {
					size = formatMb.format(new Object[]{(float) child.getSize() / (1 << 10)});
				} else {
					size = child.getSize() + " kB";
				}
			}
			DateFormat df = app.getResourceManager().getDateFormat();
			long fd = child.getFileDate();
			if (fd > 0) {
				date = (df.format(new Date(fd)));
			}
			TextView sizeText = (TextView) v.findViewById(R.id.date_and_size_details);
			sizeText.setText(date + " \u2022 " + size);

		} else {
			v.findViewById(R.id.read_section).setVisibility(View.VISIBLE);
			v.findViewById(R.id.unknown_section).setVisibility(View.GONE);
			TextView time = (TextView) v.findViewById(R.id.time);
			TextView distance = (TextView) v.findViewById(R.id.distance);
			TextView pointsCount = (TextView) v.findViewById(R.id.points_count);
			pointsCount.setText(analysis.wptPoints + "");
			if (analysis.totalDistanceMoving != 0) {
				distance.setText(OsmAndFormatter.getFormattedDistance(analysis.totalDistanceMoving, app));
			} else {
				distance.setText(OsmAndFormatter.getFormattedDistance(analysis.totalDistance, app));
			}

			if (analysis.isTimeSpecified()) {
				if (analysis.isTimeMoving()) {
					time.setText(Algorithms.formatDuration((int) (analysis.timeMoving / 1000)) + "");
				} else {
					time.setText(Algorithms.formatDuration((int) (analysis.timeSpan / 1000)) + "");
				}
			} else {
				time.setText("");
			}
		}

		TextView descr = ((TextView) v.findViewById(R.id.description));
		if (child.isExpanded()) {
			descr.setVisibility(View.VISIBLE);
			descr.setText(child.getHtmlDescription());
		} else {
			descr.setVisibility(View.GONE);
		}

		v.findViewById(R.id.check_item).setVisibility(View.GONE);
	}

}
