package net.osmand.plus.measurementtool;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import net.osmand.AndroidUtils;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.QuadPoint;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.GPXUtilities.WptPt;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.R;
import net.osmand.plus.views.ContextMenuLayer;
import net.osmand.plus.views.OsmandMapLayer;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.util.MapUtils;

import java.util.LinkedList;
import java.util.List;

import gnu.trove.list.array.TIntArrayList;

public class MeasurementToolLayer extends OsmandMapLayer implements ContextMenuLayer.IContextMenuProvider {

	private OsmandMapTileView view;
	private boolean inMeasurementMode;
	private boolean inMovePointMode;
	private boolean inAddPointAfterMode;
	private boolean inAddPointBeforeMode;
	private final LinkedList<WptPt> measurementPoints = new LinkedList<>();
	private Bitmap centerIconDay;
	private Bitmap centerIconNight;
	private Bitmap pointIcon;
	private Bitmap applyingPointIcon;
	private Paint bitmapPaint;
	private final RenderingLineAttributes lineAttrs = new RenderingLineAttributes("measureDistanceLine");
	private final Path path = new Path();
	private int marginX;
	private int marginY;
	private final TIntArrayList tx = new TIntArrayList();
	private final TIntArrayList ty = new TIntArrayList();
	private OnSingleTapListener singleTapListener;
	private OnEnterMovePointModeListener enterMovePointModeListener;
	private int selectedPointPos = -1;
	private WptPt selectedCachedPoint;
	private LatLon pressedPointLatLon;

	@Override
	public void initLayer(OsmandMapTileView view) {
		this.view = view;

		centerIconDay = BitmapFactory.decodeResource(view.getResources(), R.drawable.map_ruler_center_day);
		centerIconNight = BitmapFactory.decodeResource(view.getResources(), R.drawable.map_ruler_center_night);
		pointIcon = BitmapFactory.decodeResource(view.getResources(), R.drawable.map_pedestrian_location);
		applyingPointIcon = BitmapFactory.decodeResource(view.getResources(), R.drawable.map_mapillary_location);

		bitmapPaint = new Paint();
		bitmapPaint.setAntiAlias(true);
		bitmapPaint.setDither(true);
		bitmapPaint.setFilterBitmap(true);

		marginY = pointIcon.getHeight() / 2;
		marginX = pointIcon.getWidth() / 2;
	}

	void setOnSingleTapListener(OnSingleTapListener listener) {
		this.singleTapListener = listener;
	}

	void setOnEnterMovePointModeListener(OnEnterMovePointModeListener listener) {
		this.enterMovePointModeListener = listener;
	}

	WptPt getSelectedCachedPoint() {
		return selectedCachedPoint;
	}

	int getSelectedPointPos() {
		return selectedPointPos;
	}

	public boolean isInMeasurementMode() {
		return inMeasurementMode;
	}

	boolean isInMovePointMode() {
		return inMovePointMode;
	}

	boolean isInAddPointAfterMode() {
		return inAddPointAfterMode;
	}

	boolean isInAddPointBeforeMode() {
		return inAddPointBeforeMode;
	}

	void setInMeasurementMode(boolean inMeasurementMode) {
		this.inMeasurementMode = inMeasurementMode;
	}

	public int getPointsCount() {
		return measurementPoints.size();
	}

	public LinkedList<WptPt> getMeasurementPoints() {
		return measurementPoints;
	}

	public void setMeasurementPoints(List<WptPt> points) {
		measurementPoints.clear();
		measurementPoints.addAll(points);
	}

	String getDistanceSt() {
		float dist = 0;
		if (measurementPoints.size() > 0) {
			for (int i = 1; i < measurementPoints.size(); i++) {
				dist += MapUtils.getDistance(measurementPoints.get(i - 1).lat, measurementPoints.get(i - 1).lon,
						measurementPoints.get(i).lat, measurementPoints.get(i).lon);
			}
		}
		return OsmAndFormatter.getFormattedDistance(dist, view.getApplication());
	}

	@Override
	public boolean onSingleTap(PointF point, RotatedTileBox tileBox) {
		if (singleTapListener != null) {
			if (inMeasurementMode && !inMovePointMode && !inAddPointAfterMode && !inAddPointBeforeMode) {
				selectPoint(point.x, point.y);
				if (selectedPointPos != -1) {
					singleTapListener.onSelectPoint();
				} else {
					pressedPointLatLon = tileBox.getLatLonFromPixel(point.x, point.y);
					singleTapListener.onAddPoint();
				}
			}
		}
		return false;
	}

	void clearSelection() {
		selectedPointPos = -1;
		selectedCachedPoint = null;
	}

	@Override
	public boolean onLongPressEvent(PointF point, RotatedTileBox tileBox) {
		if (inMeasurementMode) {
			if (!inMovePointMode && !inAddPointAfterMode && !inAddPointBeforeMode && measurementPoints.size() > 0) {
				selectPoint(point.x, point.y);
				if (selectedCachedPoint != null && selectedPointPos != -1) {
					enterMovingPointMode();
					if (inMeasurementMode && inMovePointMode && enterMovePointModeListener != null) {
						enterMovePointModeListener.onEnterMovePointMode();
					}
				}
			}
		}
		return false;
	}

	void enterMovingPointMode() {
		inMovePointMode = true;
		moveMapToPoint(selectedPointPos);
	}

	void enterAddingPointAfterMode() {
		inAddPointAfterMode = true;
		moveMapToPoint(selectedPointPos);
	}

	void enterAddingPointBeforeMode() {
		inAddPointBeforeMode = true;
		moveMapToPoint(selectedPointPos);
	}

	private void selectPoint(double x, double y) {
		clearSelection();
		RotatedTileBox tb = view.getCurrentRotatedTileBox();
		double lowestDistance = AndroidUtils.dpToPx(view.getContext(), 20);
		for (int i = 0; i < measurementPoints.size(); i++) {
			WptPt pt = measurementPoints.get(i);
			if (tb.containsLatLon(pt.getLatitude(), pt.getLongitude())) {
				double xDiff = tb.getPixXFromLonNoRot(pt.getLongitude()) - x;
				double yDiff = tb.getPixYFromLatNoRot(pt.getLatitude()) - y;
				double distToPoint = Math.sqrt(Math.pow(xDiff, 2) + Math.pow(yDiff, 2));
				if (distToPoint < lowestDistance) {
					lowestDistance = distToPoint;
					selectedCachedPoint = new WptPt(pt);
					selectedPointPos = i;
				}
			}
		}
	}

	void selectPoint(int position) {
		clearSelection();
		selectedCachedPoint = new WptPt(measurementPoints.get(position));
		selectedPointPos = position;
	}

	@Override
	public void onDraw(Canvas canvas, RotatedTileBox tb, DrawSettings settings) {
		if (inMeasurementMode) {
			lineAttrs.updatePaints(view, settings, tb);
			if (!inMovePointMode && !inAddPointAfterMode && !inAddPointBeforeMode) {
				drawCenterIcon(canvas, tb, tb.getCenterPixelPoint(), settings.isNightMode());
			}

			if (measurementPoints.size() > 0) {
				path.reset();
				tx.reset();
				ty.reset();
				for (int i = 0; i < measurementPoints.size(); i++) {
					WptPt pt = measurementPoints.get(i);
					int locX;
					int locY;
					if (selectedPointPos == i && (inMovePointMode || inAddPointAfterMode || inAddPointBeforeMode)) {
						locX = tb.getCenterPixelX();
						locY = tb.getCenterPixelY();
					} else {
						locX = tb.getPixXFromLonNoRot(pt.lon);
						locY = tb.getPixYFromLatNoRot(pt.lat);
					}
					if (i == 0) {
						if (inAddPointAfterMode) {
							int previousLocX = tb.getPixXFromLonNoRot(pt.lon);
							int previousLocY = tb.getPixYFromLatNoRot(pt.lat);
							path.moveTo(previousLocX, previousLocY);
							tx.add(previousLocX);
							ty.add(previousLocY);
							path.lineTo(locX, locY);
							tx.add(locX);
							ty.add(locY);
						} else if (inAddPointBeforeMode) {
							path.moveTo(locX, locY);
							tx.add(locX);
							ty.add(locY);
							int followingLocX = tb.getPixXFromLonNoRot(pt.lon);
							int followingLocY = tb.getPixYFromLatNoRot(pt.lat);
							path.lineTo(followingLocX, followingLocY);
							tx.add(followingLocX);
							ty.add(followingLocY);
						} else {
							path.moveTo(locX, locY);
							tx.add(locX);
							ty.add(locY);
						}
					} else {
						if (inAddPointAfterMode) {
							int previousLocX = tb.getPixXFromLonNoRot(pt.lon);
							int previousLocY = tb.getPixYFromLatNoRot(pt.lat);
							path.lineTo(previousLocX, previousLocY);
							tx.add(previousLocX);
							ty.add(previousLocY);
							path.lineTo(locX, locY);
							tx.add(locX);
							ty.add(locY);
						} else if (inAddPointBeforeMode) {
							path.lineTo(locX, locY);
							tx.add(locX);
							ty.add(locY);
							int followingLocX = tb.getPixXFromLonNoRot(pt.lon);
							int followingLocY = tb.getPixYFromLatNoRot(pt.lat);
							path.lineTo(followingLocX, followingLocY);
							tx.add(followingLocX);
							ty.add(followingLocY);
						} else {
							path.lineTo(locX, locY);
							tx.add(locX);
							ty.add(locY);
						}
					}
				}
				if (!inMovePointMode && !inAddPointAfterMode && !inAddPointBeforeMode) {
					path.lineTo(tb.getCenterPixelX(), tb.getCenterPixelY());
					tx.add(tb.getCenterPixelX());
					ty.add(tb.getCenterPixelY());
				}
				calculatePath(tb, tx, ty, path);
				canvas.drawPath(path, lineAttrs.paint);

				WptPt pointToDrawOnTop = null;
				for (int i = 0; i < measurementPoints.size(); i++) {
					WptPt pt = measurementPoints.get(i);
					if (inMovePointMode && i == selectedPointPos) {
						pointToDrawOnTop = pt;
					} else {
						if (tb.containsLatLon(pt.lat, pt.lon)) {
							int locX = tb.getPixXFromLonNoRot(pt.lon);
							int locY = tb.getPixYFromLatNoRot(pt.lat);
							canvas.drawBitmap(pointIcon, locX - marginX, locY - marginY, bitmapPaint);
						}
					}
				}
				if (inAddPointAfterMode || inAddPointBeforeMode || pointToDrawOnTop != null) {
					int locX = tb.getCenterPixelX();
					int locY = tb.getCenterPixelY();
					canvas.drawBitmap(applyingPointIcon, locX - marginX, locY - marginY, bitmapPaint);
				}
			}
		}
	}

	void exitMovePointMode() {
		inMovePointMode = false;
	}

	void exitAddPointAfterMode() {
		inAddPointAfterMode = false;
	}

	void exitAddPointBeforeMode() {
		inAddPointBeforeMode = false;
	}

	private void drawCenterIcon(Canvas canvas, RotatedTileBox tb, QuadPoint center, boolean nightMode) {
		canvas.rotate(-tb.getRotate(), center.x, center.y);
		if (nightMode) {
			canvas.drawBitmap(centerIconNight, center.x - centerIconNight.getWidth() / 2,
					center.y - centerIconNight.getHeight() / 2, bitmapPaint);
		} else {
			canvas.drawBitmap(centerIconDay, center.x - centerIconDay.getWidth() / 2,
					center.y - centerIconDay.getHeight() / 2, bitmapPaint);
		}
		canvas.rotate(tb.getRotate(), center.x, center.y);
	}

	public WptPt addCenterPoint(int position) {
		RotatedTileBox tb = view.getCurrentRotatedTileBox();
		LatLon l = tb.getLatLonFromPixel(tb.getCenterPixelX(), tb.getCenterPixelY());
		WptPt pt = new WptPt();
		pt.lat = l.getLatitude();
		pt.lon = l.getLongitude();
		boolean allowed = measurementPoints.size() == 0 || !measurementPoints.get(measurementPoints.size() - 1).equals(pt);
		if (allowed) {
			measurementPoints.add(position, pt);
			return pt;
		}
		return null;
	}

	public WptPt addPoint(int position) {
		if (pressedPointLatLon != null) {
			WptPt pt = new WptPt();
			pt.lat = pressedPointLatLon.getLatitude();
			pt.lon = pressedPointLatLon.getLongitude();
			boolean allowed = measurementPoints.size() == 0 || !measurementPoints.get(measurementPoints.size() - 1).equals(pt);
			if (allowed) {
				measurementPoints.add(position, pt);
				moveMapToPoint(position);
				pressedPointLatLon = null;
				return pt;
			}
		}
		return null;
	}

	WptPt getMovedPointToApply() {
		RotatedTileBox tb = view.getCurrentRotatedTileBox();
		LatLon latLon = tb.getCenterLatLon();
		WptPt pt = measurementPoints.get(selectedPointPos);
		pt.lat = latLon.getLatitude();
		pt.lon = latLon.getLongitude();
		return pt;
	}

	public void moveMapToPoint(int pos) {
		if (measurementPoints.size() > 0) {
			if (pos >= measurementPoints.size()) {
				pos = measurementPoints.size() - 1;
			} else if (pos < 0) {
				pos = 0;
			}
			WptPt pt = measurementPoints.get(pos);
			view.getAnimatedDraggingThread().startMoving(pt.getLatitude(), pt.getLongitude(), view.getZoom(), true);
		}
	}

	public void refreshMap() {
		view.refreshMap();
	}

	@Override
	public void destroyLayer() {

	}

	@Override
	public boolean drawInScreenPixels() {
		return false;
	}

	@Override
	public void collectObjectsFromPoint(PointF point, RotatedTileBox tileBox, List<Object> o) {

	}

	@Override
	public LatLon getObjectLocation(Object o) {
		return null;
	}

	@Override
	public PointDescription getObjectName(Object o) {
		return null;
	}

	@Override
	public boolean disableSingleTap() {
		return isInMeasurementMode();
	}

	@Override
	public boolean disableLongPressOnMap() {
		return isInMeasurementMode();
	}

	@Override
	public boolean isObjectClickable(Object o) {
		return !isInMeasurementMode();
	}

	interface OnSingleTapListener {

		void onAddPoint();

		void onSelectPoint();
	}

	interface OnEnterMovePointModeListener {
		void onEnterMovePointMode();
	}
}
