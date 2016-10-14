package com.airbnb.lottie.animation;

import android.graphics.PointF;
import android.support.v4.view.animation.PathInterpolatorCompat;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import com.airbnb.lottie.L;
import com.airbnb.lottie.utils.LottieKeyframeAnimation;
import com.airbnb.lottie.utils.LottiePathKeyframeAnimation;
import com.airbnb.lottie.utils.Observable;
import com.airbnb.lottie.utils.SegmentedPath;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class LottieAnimatablePathValue implements LottieAnimatableValue<PointF> {

    private final Observable<PointF> observable = new Observable<>();
    private final List<Float> keyTimes = new ArrayList<>();
    private final List<Interpolator> interpolators = new ArrayList<>();
    private final long compDuration;
    private final int frameRate;
    private final boolean isDp;

    private PointF initialPoint;
    private final SegmentedPath animationPath = new SegmentedPath();
    private long delay;
    private long duration;
    private long startFrame;
    private long durationFrames;

    public LottieAnimatablePathValue(JSONObject pointValues, int frameRate, long compDuration) {
        this(pointValues, frameRate, compDuration, true);
    }

    @SuppressWarnings("EmptyCatchBlock")
    public LottieAnimatablePathValue(JSONObject pointValues, int frameRate, long compDuration, boolean isDp) {
        this.compDuration = compDuration;
        this.frameRate = frameRate;
        this.isDp = isDp;

        Object value = null;
        try {
            value = pointValues.get("k");
        } catch (JSONException e) { }
        if (value == null) {
            throw new IllegalArgumentException("Point values have no keyframes.");
        }

        if (value instanceof JSONArray) {
            Object firstObject = null;
            try {
                firstObject = ((JSONArray) value).get(0);
            } catch (JSONException e) { }
            if (firstObject == null) {
                throw new IllegalArgumentException("Unable to parse value.");
            }

            if (firstObject instanceof JSONObject && ((JSONObject) firstObject).has("t")) {
                // Keyframes
                buildAnimationForKeyframes((JSONArray) value);
            } else {
                // Single Value, no animation
                initialPoint = pointFromValueArray((JSONArray) value);
                observable.setValue(initialPoint);
            }
        }
    }

    private void buildAnimationForKeyframes(JSONArray keyframes) {
        try {
            for (int i = 0; i < keyframes.length(); i++) {
                JSONObject kf = keyframes.getJSONObject(i);
                if (kf.has("t")) {
                    startFrame = kf.getLong("t");
                    break;
                }
            }

            for (int i = keyframes.length() - 1; i >= 0; i--) {
                JSONObject keyframe = keyframes.getJSONObject(i);
                if (keyframe.has("t")) {
                    long endFrame = keyframe.getLong("t");
                    if (endFrame <= startFrame) {
                        throw new IllegalStateException("Invalid frame compDuration " + startFrame + "->" + endFrame);
                    }
                    durationFrames = endFrame - startFrame;
                    duration = (long) (durationFrames / (float) frameRate * 1000);
                    delay = (long) (startFrame / (float) frameRate * 1000);
                    break;
                }
            }

            boolean addStartValue = true;
            boolean addTimePadding =  false;
            PointF outPoint = null;

            for (int i = 0; i < keyframes.length(); i++) {
                JSONObject keyframe = keyframes.getJSONObject(i);
                long frame = keyframe.getLong("t");
                float timePercentage = (float) (frame - startFrame) / (float) durationFrames;

                if (outPoint != null) {
                    PointF vertex = outPoint;
                    animationPath.lineTo(vertex.x, vertex.y);
                    interpolators.add(new LinearInterpolator());
                    outPoint = null;
                }

                PointF startPoint = keyframe.has("s") ? pointFromValueArray(keyframe.getJSONArray("s")) : new PointF();
                if (addStartValue) {
                    if (i == 0) {
                        animationPath.moveTo(startPoint.x, startPoint.y);
                        initialPoint = startPoint;
                        observable.setValue(initialPoint);
                    } else {
                        animationPath.lineTo(startPoint.x, startPoint.y);
                        interpolators.add(new LinearInterpolator());
                    }
                    addStartValue = false;
                }

                if (addTimePadding) {
                    float holdPercentage = timePercentage - 0.00001f;
                    keyTimes.add(holdPercentage);
                    addTimePadding = false;
                }

                PointF cp1;
                PointF cp2;
                if (keyframe.has("e")) {
                    cp1 = keyframe.has("to") ? pointFromValueArray(keyframe.getJSONArray("to")) : null;
                    cp2 = keyframe.has("ti") ? pointFromValueArray(keyframe.getJSONArray("ti")) : null;
                    PointF vertex = pointFromValueArray(keyframe.getJSONArray("e"));
                    if (cp1 != null && cp2 != null) {
                        animationPath.cubicTo(
                                startPoint.x + cp1.x, startPoint.y + cp1.y,
                                vertex.x + cp2.x, vertex.y + cp2.y,
                                vertex.x, vertex.y);
                    } else {
                        animationPath.lineTo(vertex.x, vertex.y);
                    }

                    Interpolator interpolator;
                    if (keyframe.has("o") && keyframe.has("i")) {
                        cp1 = pointFromValueObject(keyframe.getJSONObject("o"));
                        cp2 = pointFromValueObject(keyframe.getJSONObject("i"));
                        float unScale = isDp ? L.SCALE : 1f;
                        interpolator = PathInterpolatorCompat.create(cp1.x / unScale, cp1.y / unScale, cp2.x / unScale, cp2.y / unScale);
                    } else {
                        interpolator = new LinearInterpolator();
                    }
                    interpolators.add(interpolator);
                }

                keyTimes.add(timePercentage);

                if (keyframe.has("h") && keyframe.getInt("h") == 1) {
                    outPoint = startPoint;
                    addStartValue = true;
                    addTimePadding = true;
                }
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to parse keyframes " + keyframes, e);
        }
    }

    private PointF pointFromValueArray(JSONArray values) {
        if (values.length() >= 2) {
            try {
                float scale = isDp ? L.SCALE : 1;
                return new PointF((float) values.getDouble(0) * scale, (float) values.getDouble(1) * scale);
            } catch (JSONException e) {
                throw new IllegalArgumentException("Unable to parse point for " + values, e);
            }
        }

        return new PointF();
    }

    private PointF pointFromValueObject(JSONObject value) {
        try {
            Object x = value.get("x");
            Object y = value.get("y");

            PointF point = new PointF();
            if (x instanceof JSONArray) {
                point.x = (float) ((JSONArray) x).getDouble(0);
            } else {
                if (x instanceof Integer) {
                    point.x = (Integer) x;
                } else {
                    point.x = new Float((Double) x);
                }
            }

            if (y instanceof JSONArray) {
                point.y = (float) ((JSONArray) y).getDouble(0);
            } else {
                if (y instanceof Integer) {
                    point.y = (Integer) y;
                } else {
                    point.y = new Float((Double) y);
                }
            }

            if (isDp) {
                point.y *= isDp ? L.SCALE : 1f;
                point.x *= isDp ? L.SCALE : 1f;
            }

            return point;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Unable to parse point for " + value);
        }
    }

    @Override
    public Observable<PointF> getObservable() {
        return observable;
    }

    @Override
    public LottieKeyframeAnimation animationForKeyPath() {
        if (!hasAnimation()) {
            return null;
        }

        LottieKeyframeAnimation<PointF> animation = new LottiePathKeyframeAnimation(duration, compDuration, keyTimes, animationPath, interpolators);
        animation.setStartDelay(delay);
        animation.addUpdateListener(new LottieKeyframeAnimation.AnimationListener<PointF>() {
            @Override
            public void onValueChanged(PointF progress) {
                observable.setValue(progress);
            }
        });
        return animation;
    }

    @Override
    public boolean hasAnimation() {
        return animationPath.hasSegments();
    }

    @Override
    public String toString() {
        return "LottieAnimatablePathValue{" + "initialPoint=" + initialPoint + '}';
    }
}