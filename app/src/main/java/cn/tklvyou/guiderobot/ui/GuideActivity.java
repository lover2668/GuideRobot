package cn.tklvyou.guiderobot.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.google.gson.Gson;
import com.slamtec.slamware.AbstractSlamwarePlatform;
import com.slamtec.slamware.action.ActionStatus;
import com.slamtec.slamware.action.IAction;
import com.slamtec.slamware.action.IMoveAction;
import com.slamtec.slamware.geometry.Line;
import com.slamtec.slamware.robot.ArtifactUsage;
import com.slamtec.slamware.robot.CompositeMap;
import com.slamtec.slamware.robot.Location;
import com.slamtec.slamware.robot.MoveOption;
import com.slamtec.slamware.robot.Pose;
import com.slamtec.slamware.robot.Rotation;
import com.slamtec.slamware.sdp.CompositeMapHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import cn.tklvyou.guiderobot.RobotAction;
import cn.tklvyou.guiderobot.adapter.LogAdapter;
import cn.tklvyou.guiderobot.api.RetrofitHelper;
import cn.tklvyou.guiderobot.api.RxSchedulers;
import cn.tklvyou.guiderobot.base.BaseActivity;
import cn.tklvyou.guiderobot.base.BaseResult;
import cn.tklvyou.guiderobot.base.MyApplication;
import cn.tklvyou.guiderobot.constant.HomeConstant;
import cn.tklvyou.guiderobot.log.TourCooLogUtil;
import cn.tklvyou.guiderobot.log.widget.config.LogLevel;
import cn.tklvyou.guiderobot.manager.GlideManager;
import cn.tklvyou.guiderobot.manager.Robot;
import cn.tklvyou.guiderobot.model.ControllerModel;
import cn.tklvyou.guiderobot.model.DaoSession;
import cn.tklvyou.guiderobot.model.LocationModel;
import cn.tklvyou.guiderobot.model.LogInfo;
import cn.tklvyou.guiderobot.model.NavLocation;
import cn.tklvyou.guiderobot.threadpool.ThreadPoolManager;
import cn.tklvyou.guiderobot.utils.MathUtil;
import cn.tklvyou.guiderobot.utils.MotorController;
import cn.tklvyou.guiderobot_new.R;
import cn.tklvyou.serialportlibrary.SerialPort;

import static cn.tklvyou.guiderobot.CommonConfig.DEBUG_MODE;
import static cn.tklvyou.guiderobot.RobotAction.ACTION_ROTATION_TURN_LEFT_30;
import static cn.tklvyou.guiderobot.RobotAction.ACTION_ROTATION_TURN_LEFT_45;
import static cn.tklvyou.guiderobot.RobotAction.ACTION_ROTATION_TURN_LEFT_90;
import static cn.tklvyou.guiderobot.RobotAction.ACTION_ROTATION_TURN_RIGHT_30;
import static cn.tklvyou.guiderobot.RobotAction.ACTION_ROTATION_TURN_RIGHT_45;
import static cn.tklvyou.guiderobot.RobotAction.ACTION_ROTATION_TURN_RIGHT_90;
import static cn.tklvyou.guiderobot.RobotAction.TYPE_ACTION;
import static cn.tklvyou.guiderobot.RobotAction.TYPE_TEXT;
import static cn.tklvyou.guiderobot.constant.HomeConstant.MSG_CLOSE_LOADING;
import static cn.tklvyou.guiderobot.constant.HomeConstant.MSG_LOG_D;
import static cn.tklvyou.guiderobot.constant.HomeConstant.MSG_LOG_E;
import static cn.tklvyou.guiderobot.constant.HomeConstant.MSG_LOG_I;
import static cn.tklvyou.guiderobot.constant.HomeConstant.MSG_SHOW_LOADING;
import static cn.tklvyou.guiderobot.constant.HomeConstant.MSG_START;
import static cn.tklvyou.guiderobot.constant.HomeConstant.MSG_TOAST;
import static cn.tklvyou.guiderobot.constant.RequestConstant.REQUEST_ERROR;
import static cn.tklvyou.guiderobot.constant.RequestConstant.REQUEST_SUCCESS;
import static cn.tklvyou.guiderobot.log.widget.config.LogLevel.TYPE_WARN;
import static java.lang.Integer.MIN_VALUE;

/**
 * @author :JenkinsZhou
 * @description :
 * @company :途酷科技
 * @date 2019年09月05日13:50
 * @Email: 971613168@qq.com
 */
public class GuideActivity extends BaseActivity implements View.OnClickListener {
    private static final long END = 0L;
    private static final long ONE_SECOND = 1000L;
    public static final String TAG = "GuideActivity";
    private ImageView ivShow;
    private ImageView btnStartNav;
    private boolean isTip;
    private DaoSession daoSession;
    private AbstractSlamwarePlatform slamWarePlatform;
    private NavLocation mCurrentPositionInfo;
    private List<LogInfo> logList = new ArrayList<>();
    private LinearLayout llLogArea;
    private RecyclerView rvLog;
    private LogAdapter logAdapter;
    private long currentLocationId;
    private boolean testFinish = false;
    /**
     * 语音是否说完
     */
    private boolean speakFinish = false;
    /**
     * 数据库存储的位置点
     */
    private List<NavLocation> navLocationList;
    private MainHandler mHandler = new MainHandler(this);

    private List<LocationModel.ContentBean> mCurrentActionList;

    private MotorController motorController;
    private SerialPort serialPort;


    @Override
    protected int getActivityLayoutID() {
        return R.layout.activity_main;
    }

    @Override
    protected void initView() {
        mHandler = new MainHandler(this);
        initLog();
        slamWarePlatform = Robot.getInstance().getSlamWarePlatform();
        motorController = ((MyApplication) getApplication()).getMotorController();
        serialPort = ((MyApplication) getApplication()).getLedController();
        if (slamWarePlatform == null) {
          /*  ToastUtils.showShort("未实例化机器人");
            finish();
            return;*/
            ToastUtils.showShort("未实例化机器人");
        }
        ivShow = findViewById(R.id.ivShow);
        btnStartNav = findViewById(R.id.btnStartNav);
        btnStartNav.setOnClickListener(this);
        initData();
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnStartNav:
                logI(TAG, "点击了");
                showLoading("阿双方均氨基酸法律框架");
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        closeLoading();
                    }
                }, 2000);
                break;
            default:
                break;
        }
    }


    private void initData() {
        daoSession = ((MyApplication) getApplication()).getDaoSession();
        navLocationList = daoSession.getNavLocationDao().queryBuilder().list();
        if (navLocationList == null) {
            ToastUtils.showShort("数据库初始化异常");
            finish();
            return;
        }
        initRobotMap();
    }

    /**
     * 初始化机器人自动寻路需要的地图
     */
    private void initRobotMap() {
        ThreadPoolManager.getThreadPoolProxy().execute(new Runnable() {
            @Override
            public void run() {
                LogUtils.i(TAG, "initRobotMap");
                String path = "/sdcard/robot/map.stcm";
                CompositeMapHelper compositeMapHelper = new CompositeMapHelper();
                CompositeMap compositeMap = compositeMapHelper.loadFile(path);
                String poseJson = SPUtils.getInstance().getString("pose");
                if (!TextUtils.isEmpty(poseJson) && compositeMap != null) {
                    Pose pose = new Gson().fromJson(poseJson, Pose.class);
                    try {
                        slamWarePlatform.setCompositeMap(compositeMap, pose);
                        //加载成功后 则获取后台配置的第一个点
                        TourCooLogUtil.i(TAG, "地图加载成功");
                        sendEmptyMsg(HomeConstant.MSG_START);
                    } catch (Exception e) {
                        ToastUtils.showShort("地图初始化异常 原因---->" + e.toString());
                        logE(TAG, "地图初始化异常");
                    }
                    //加载地图和上次pose信息后
                } else {
                    ToastUtils.showShort("数据加载异常");
                    logE(TAG, "数据加载异常");
                }
            }
        });
    }


    private static class MainHandler extends Handler {
        WeakReference<Activity> mWeakReference;

        private MainHandler(Activity activity) {
            mWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (mWeakReference != null) {
                GuideActivity activity = (GuideActivity) mWeakReference.get();
                if (activity == null) {
                    return;
                }
                switch (msg.what) {
                    case HomeConstant.MSG_START:
                        activity.requestLocationInfo(activity.currentLocationId);
                        break;
                    case MSG_TOAST:
                        ToastUtils.showShort((String) msg.obj);
                        break;
                    case MSG_SHOW_LOADING:
                        activity.showLoading((String) msg.obj);
                        break;
                    case MSG_CLOSE_LOADING:
                        activity.closeLoading();
                        break;
                    case MSG_LOG_D:
                        activity.logD(TAG, msg.obj);
                        break;
                    case MSG_LOG_I:
                        activity.logI(TAG, msg.obj);
                        break;
                    case MSG_LOG_E:
                        activity.logE(TAG, msg.obj);
                        break;
                    default:
                        break;
                }
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
    }


    private void sendEmptyMsg(int what) {
        mHandler.sendEmptyMessage(what);
    }

    /**
     * 请求网络 获取第一个讲解点的数据信息
     */
    @SuppressLint("CheckResult")
    private void requestLocationInfo(long id) {
        showLoading("正在请求位置信息");
        if (id < 0) {
            logI(TAG, "所有位置点讲解全部结束");
            ToastUtils.showShort("本次讲解全部结束");
            isTip = true;
            speckTextSynthesis("讲解点全部执行完毕!", true);
            closeLoading();
            return;
        }
        setViewVisible(btnStartNav, false);
        RetrofitHelper.getInstance().getServer()
                .getLocationMessage(id)
                .compose(RxSchedulers.applySchedulers())
                .subscribe(result -> {
                    closeLoading();
                    switch (result.getStatus()) {
                        case REQUEST_ERROR:
                            ToastUtils.showShort(result.getErrmsg());
                            setViewVisible(btnStartNav, true);
                            break;
                        case REQUEST_SUCCESS:
                            LocationModel currentLocation = result.getData();
                            if (currentLocation == null) {
                                logE("处理位置点", "请求的位置点为null");
                                ToastUtils.showShort("位置点信息为空");
                                return;
                            }
                            logI("处理位置点", "正在处理位置点---->" + currentLocationId);
                            currentLocationId = currentLocation.getNext();
                            logD("处理位置点", "正在处理位置点:" + currentLocationId);
                            handleLocationTarget(currentLocation);
                            break;
                        default:
                            break;
                    }
                }, throwable -> {
                    closeLoading();
                    ToastUtils.showShort("请求失败");
                    setViewVisible(btnStartNav, true);
                });
    }


    private void handleLocationTarget(LocationModel locationModel) {
        if (locationModel == null || locationModel.getLocal() == 0) {
            ToastUtils.showShort("目的地为null");
            logE(TAG, "目的地为null");
            return;
        }
        try {
            ThreadPoolManager.getThreadPoolProxy().execute(() -> {
                // 根据服务器返回的LocationModel中的当前id 去数据库寻找与之对应的NavLocation实体
                logI(TAG, "正在从数据库寻找对应位置数据");
                mCurrentPositionInfo = daoSession.getNavLocationDao().load(locationModel.getLocal());
                if (mCurrentPositionInfo == null) {
                    ToastUtils.showShort("未获取到位置信息");
                    logE(TAG, "未获取到位置信息");
                    logD(TAG, "延迟10秒后直接请求下一个讲解点信息");
                    delay(ONE_SECOND * 10);
                    logI(TAG, "即将请求下一个讲解点信息");
                    sendEmptyMsg(MSG_START);
                    return;
                }
                logI(TAG, "正在加载位置图片...");
                loadImage(locationModel.getThumb(), ivShow);
                logI(TAG, "等待2秒后开始说话");
                delay(ONE_SECOND * 2);
                speckTextSynthesis("5秒后开始模拟寻路", false);
                delay(ONE_SECOND * 5);
                logI(TAG, "等待5秒后开始前往当前目的地");
                delay(ONE_SECOND * 5);
                logI(TAG, "即将执行goToTheDestination()");
                goToTheDestination(mCurrentPositionInfo);
                logD(TAG, "已经到达目的地 2秒后开始执行动作指令...");
                delay(ONE_SECOND * 2);
                //已经到达目的地 开始执行指令
                handleAction(locationModel);
            });
        } catch (Exception e) {
            logE(TAG, "handleLocationTarget()方法异常:原因---->" + e.toString());
        }


    }


    /**
     * 前往 设置过的讲解点(该方法为阻塞方法)
     */
    private void goToTheDestination(NavLocation navLocation) {
        if (navLocation == null) {
            logE(TAG, "讲解点数据为空");
            showToast("讲解点数据为空");
            return;
        }
        try {
            logI(TAG, "准备正在行走(模拟)...2秒钟后显示弹窗");
            delay(ONE_SECOND * 2);
            showLoadingDialog("正在模拟行走...5秒后关闭弹窗");
            delay(ONE_SECOND * 5);
            closeLoadingDialog();
          /*  MoveOption moveOption = new MoveOption();
            //机器人移动的时候精确到点
            moveOption.setPrecise(true);
            moveOption.setMilestone(true);
            Pose currentPose = slamWarePlatform.getPose();
          *//*  //先获取当前位置信息
            //根据当前位置和传进来的NavLocation 构建一条虚拟路径line
            int segmentId = navLocation.getId().intValue();
            Line line = new Line(segmentId, currentPose.getX(), currentPose.getY(), navLocation.getX(), navLocation.getY());
            TourCooLogUtil.i("虚拟线路", line);
            //添加虚拟路径
            slamWarePlatform.addLine(ArtifactUsage.ArtifactUsageVirtualTrack, line);*//*
            Location location = new Location(navLocation.getX(), navLocation.getY(), navLocation.getZ());
            //todo 后面传旋转角度
            logD(TAG, "当前的位置信息:X=" + currentPose.getX() + "Y=" + currentPose.getY());
            logI(TAG, "要前往的位置信息:X=" + navLocation.getX() + "Y=" + navLocation.getY());
            action = slamWarePlatform.moveTo(location, moveOption, 0);
            ActionStatus status = action.waitUntilDone();
            if (status == ActionStatus.FINISHED) {
                showToast("本次行走结束");
                logI(TAG, "本次行走结束");
                return;
            } else {
                isTip = true;
                speckTextSynthesis("小哥哥小姐姐们，请不要挡住我的路好嘛，谢谢！", false);
                goToTheDestination(navLocation);
            }*/
            logI(TAG, "本次行走结束(模拟)");
        } catch (Exception e) {
            showToast("导航异常,异常原因--->" + e.toString());
            logE(TAG, "goToTheDestination()方法异常--->" + e.toString());
        }
    }


    private void showToast(String text) {
        Message message = mHandler.obtainMessage();
        message.what = MSG_TOAST;
        message.obj = text;
        mHandler.sendMessage(message);
    }


    private void showLoadingDialog(String text) {
        Message message = mHandler.obtainMessage();
        message.what = MSG_SHOW_LOADING;
        message.obj = text;
        mHandler.sendMessage(message);
    }


    private void closeLoadingDialog() {
        Message message = mHandler.obtainMessage();
        message.what = MSG_CLOSE_LOADING;
        mHandler.sendMessage(message);
    }

    private void logI(String tag, Object log) {
        if (!DEBUG_MODE) {
            return;
        }
        if (isMainThread()) {
            TourCooLogUtil.i(tag, log);
            logList.add(createLogInfo(log, LogLevel.TYPE_INFO));
            logAdapter.notifyItemInserted(logList.size() - 1);
            rvLog.scrollToPosition(logList.size() - 1);
        } else {
            sendMessage(MSG_LOG_I, log);
        }
    }

    private void logD(String tag, Object log) {
        if (!DEBUG_MODE) {
            return;
        }
        if (isMainThread()) {
            TourCooLogUtil.d(tag, log);
            logList.add(createLogInfo(log, LogLevel.TYPE_DEBUG));
            logAdapter.notifyItemInserted(logList.size() - 1);
            rvLog.scrollToPosition(logList.size() - 1);
        } else {
            sendMessage(MSG_LOG_D, log);
        }

    }

    private void logE(String tag, Object log) {
        if (!DEBUG_MODE) {
            return;
        }
        if (isMainThread()) {
            TourCooLogUtil.e(tag, log);
            logList.add(createLogInfo(log, LogLevel.TYPE_ERROR));
            logAdapter.notifyItemInserted(logList.size() - 1);
            rvLog.scrollToPosition(logList.size() - 1);
        } else {
            sendMessage(MSG_LOG_E, log);
        }
    }

    private void logW(String tag, Object log) {
        if (!DEBUG_MODE) {
            return;
        }
        if (isMainThread()) {
            TourCooLogUtil.e(tag, log);
            logList.add(createLogInfo(log, TYPE_WARN));
            logAdapter.notifyItemInserted(logList.size() - 1);
            rvLog.scrollToPosition(logList.size() - 1);
        } else {
            sendMessage(TYPE_WARN, log);
        }
    }

    private void sendMessage(int what, Object object) {
        Message message = mHandler.obtainMessage();
        message.what = what;
        message.obj = object;
        mHandler.sendMessage(message);
    }


    private void initLog() {
        logList.clear();
        llLogArea = findViewById(R.id.llLogArea);
        rvLog = findViewById(R.id.rvLog);
        rvLog.setLayoutManager(new LinearLayoutManager(this));
        logAdapter = new LogAdapter();
        logAdapter.bindToRecyclerView(rvLog);
        logAdapter.setNewData(logList);
        setViewGone(llLogArea, DEBUG_MODE);
    }

    private LogInfo createLogInfo(Object log, int logLevel) {
        LogInfo logInfo = new LogInfo();
        logInfo.setTime(System.currentTimeMillis() + "");
        logInfo.setLogContent(log.toString());
        logInfo.setLogLevel(logLevel);
        return logInfo;
    }


    private void delay(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理后台给出的行为指令集
     */
    private void handleAction(LocationModel locationModel) {
        if (locationModel == null || locationModel.getContent() == null) {
            ToastUtils.showShort("服务器数据异常");
            logE("", "服务器LocationModel数据为null");
            return;
        }
        mCurrentActionList = locationModel.getContent();
        if (mCurrentActionList.isEmpty()) {
            logW(TAG, "当前讲解点的指令集为空");
            return;
        }
        //真正执行指令的方法
        executeActionByActionList(mCurrentActionList);
    }


    /**
     * 递归执行当前讲解点的所有动作
     */
    private void executeActionByActionList(List<LocationModel.ContentBean> actionList) {
        if (actionList == null) {
            logE("", "指令集为null");
            showToast("当前指令集为空");
            return;
        }
        if (actionList.isEmpty()) {
            logI("", "本次指令集全部执行完毕");
            showToast("本次指令集全部执行完毕");
            sendEmptyMsg(HomeConstant.MSG_START);
            return;
        }
        //每次取出指令集中的第一条指令并执行
        LocationModel.ContentBean actionBean = actionList.get(0);
        if (actionBean == null) {
            actionList.remove(0);
            executeActionByActionList(actionList);
            return;
        }
        switch (actionBean.getType()) {
            case TYPE_TEXT:
                //语音指令
                if (TextUtils.isEmpty(actionBean.getValue())) {
                    showToast("语音文本为空");
                    logE(TAG, "语音文本为空");
                    return;
                }
                //执行语音指令
                isTip = false;
                //因为该指令即将执行 所以需要先移除当前指令
                actionList.remove(actionBean);
                logI(TAG, "即将开始执行语音指令（即将开始说话）...");
                //将语音说话状态置为未说完状态
                speakFinish = false;
                speckTextSynthesis(actionBean.getValue(), false);
                //如果是语音指令 则必须要等到语音说完才能执行下一步 否则一直阻塞线程
                while ((!speakFinish)) {
                    delay(ONE_SECOND);
                    logD(TAG, "等待说话结束...——>说话是否结束 = " + speakFinish);
                }
                logI(TAG, "已经退出死循环...");
                break;
            case TYPE_ACTION:
                //动作指令
                actionList.remove(actionBean);
                doAction(actionBean);
                logI(TAG, "本次executeActionByActionList()方法执行完毕" + "，剩余还有" + actionList.size() + "条动作需要执行");
                break;
            default:
                actionList.remove(actionBean);
                logW(TAG, "executeActionByActionList()未匹配到指令类型");
                showToast("未匹配到指令类型");
                break;
        }
        delay(ONE_SECOND);
        logD(TAG, "继续执行executeActionByActionList()方法 直到actionList内没有指令...");
        executeActionByActionList(actionList);
    }


    @Override
    public void playComplete() {
        super.playComplete();
        //说话结束 因此需要置为说话完成状态
        speakFinish = true;
        ToastUtils.showShort("小女孩说话已经结束");
        if (isTip) {
            isTip = false;
        } else {
            logD(TAG, "语音指令执行完毕");
            //语音指令执行完毕 则开始执行下一条指令
            logI(TAG, "即将继续执行executeAction(mCurrentActionList) 方法 --->也就是当前位置的下一条指令");
            logE(TAG, "语音指令执行完毕");
        }
    }

    /**
     * 做动作
     */
    private void doAction(LocationModel.ContentBean action) {
        if (action == null || !TYPE_ACTION.equals(action.getType())) {
            logE("", "动作为null或不是动作类型");
            return;
        }

        try {
            ControllerModel actionModel = RobotAction.getControllerCommand(action.getValue());
            switch (actionModel.getType()) {
                case RobotAction.LEFT_RIGHT_HANDS_ACTION:
                    motorController.sendData((byte[]) actionModel.getParams());
                    logD("doAction", "发送手臂指令成功");
                    break;
                case RobotAction.HEAD_LED_ACTION:
                    serialPort.sendDataToSerialPort((byte[]) actionModel.getParams());
                    logD("doAction", "发送头部LED灯指令成功");
                    break;
                case RobotAction.HANDED_ROTATION_ACTION:
                    //执行身体转动
                    doBodyRotating((String) actionModel.getParams());
//                    doRotationTest();
                    break;
                case RobotAction.DELAY_ACTION:
                    logI("doAction", "doAction 执行延时1.5秒");
                    showToast("当前动作是延迟1.5秒");
                    delay(1500);
                    break;
                default:
                    logW("doAction", "doAction()未匹配到动作");
                    break;
            }
        } catch (Exception e) {

            logE(TAG, "doAction()异常---->原因:" + e.toString());

        }
    }


    /**
     * 根据转动action执行对应动作（转身）
     */
    private void doBodyRotating(String action) {
        try {
            switch (action) {
                case ACTION_ROTATION_TURN_LEFT_30:
                    //身体左转30度
                    turnLeftByAngle(slamWarePlatform, 30);
                  /*  Pose pose = slamWarePlatform.getPose();
                    pose.getRotation();
                    rotation = new Rotation(0);
                    moveAction = slamWarePlatform.rotate(rotation);
                    //该方法为阻塞方法 会阻塞线程
                    moveAction.waitUntilDone();
                    logD("doBodyRotating", "左转指令执行完毕");*/
                    logD("doBodyRotating", "左转30度指令执行完毕");

                    break;
                case ACTION_ROTATION_TURN_LEFT_45:
                    //身体左转30度
                    turnLeftByAngle(slamWarePlatform, 45);
                    logD("doBodyRotating", "左转45度指令执行完毕");
                    break;
                case ACTION_ROTATION_TURN_LEFT_90:
                    //身体左转30度
                    turnLeftByAngle(slamWarePlatform, 90);
                    logD("doBodyRotating", "左转90度指令执行完毕");
                    break;
                case ACTION_ROTATION_TURN_RIGHT_30:
                    turnRightByAngle(slamWarePlatform, 30);
                    logD("doBodyRotating", "右转30度指令执行完毕");
                    break;
                case ACTION_ROTATION_TURN_RIGHT_45:
                    turnRightByAngle(slamWarePlatform, 45);
                    logD("doBodyRotating", "右转45度指令执行完毕");
                    break;
                case ACTION_ROTATION_TURN_RIGHT_90:
                    turnRightByAngle(slamWarePlatform, 90);
                    logD("doBodyRotating", "右转90度指令执行完毕");
                    break;
                default:
                    logW(TAG, "未匹配到转身指令");
                    ToastUtils.showShort("未匹配到转身指令");
                    break;
            }
        } catch (Exception e) {
            logE(TAG, "doBodyRotating()执行异常---->原因:" + e.toString());
        }
    }

    /**
     * 根据角度进行左转
     */
    private void turnLeftByAngle(AbstractSlamwarePlatform platform, int angel) {
        try {
            if (platform == null) {
                logE(TAG, "当前currentPose或platform实例为null");
                return;
            }
            logD(TAG, "准备执行左转");
            Pose currentPose = platform.getPose();
            Rotation currentRotation = currentPose.getRotation();
            float currentYaw = currentRotation.getYaw();
            logD(TAG, "当前currentYaw值:" + currentYaw);
            float yaw;
            switch (angel) {
                case 30:
                    //左转30度
                    yaw = currentYaw + (MathUtil.PI / 6);
                    break;
                case 45:
                    //左转45度
                    yaw = currentYaw + (MathUtil.PI / 4);
                    break;
                case 90:
                    //左转90度
                    yaw = currentYaw + (MathUtil.PI / 2);
                    break;
                default:
                    yaw = MIN_VALUE;
                    break;
            }
            if (yaw == MIN_VALUE) {
                showToast("未匹配到转身指令");
                logE(TAG, "未匹配到转身指令");
                return;
            }
            currentRotation.setYaw(yaw);
            IMoveAction turnAction = platform.rotateTo(currentRotation);
            //该方法为阻塞方法 会阻塞线程
            turnAction.waitUntilDone();
            logI(TAG, "左转结束");
            logD(TAG, "旋转后的currentYaw值:" + yaw);
        } catch (Exception e) {
            logE(TAG, "turnLeftByAngle()异常 原因---->" + e.toString());
        }
    }


    /**
     * 根据角度进行右转
     */
    private void turnRightByAngle(AbstractSlamwarePlatform platform, int angel) {
        try {
            if (platform == null) {
                logE(TAG, "当前currentPose或platform实例为null");
                return;
            }
            logD(TAG, "准备执行右转");
            Pose currentPose = platform.getPose();
            Rotation currentRotation = currentPose.getRotation();
            float currentYaw = currentRotation.getYaw();
            logD(TAG, "当前currentYaw值:" + currentYaw);
            float yaw;
            switch (angel) {
                case 30:
                    //右转30度
                    yaw = currentYaw + (-MathUtil.PI / 6);
                    break;
                case 45:
                    //右转45度
                    yaw = currentYaw + (-MathUtil.PI / 4);
                    break;
                case 90:
                    //右转90度
                    yaw = currentYaw + (-MathUtil.PI / 2);
                    break;
                default:
                    yaw = MIN_VALUE;
                    break;
            }
            if (yaw == MIN_VALUE) {
                showToast("未匹配到转身指令");
                logE(TAG, "未匹配到转身指令");
                return;
            }
            currentRotation.setYaw(yaw);
            IMoveAction turnAction = platform.rotateTo(currentRotation);
            //该方法为阻塞方法 会阻塞线程
            turnAction.waitUntilDone();
            logI(TAG, "右转结束");
            logD(TAG, "旋转后的currentYaw值:" + yaw);
        } catch (Exception e) {
            logE(TAG, "turnRightByAngle()异常 原因---->" + e.toString());
        }
    }

/**
 * 测试转身
 */
   /* private void doRotationTest() {
        try {
            testFinish = false;
            Rotation rotation = new Rotation();
            rotation.setYaw(MathUtil.PI*2);
            ThreadPoolManager.getThreadPoolProxy().execute(new Runnable() {
                @Override
                public void run() {
                    while (!testFinish) {
                        try {
                            logD(TAG, "当前机器人的yaw值" + slamWarePlatform.getPose().getYaw());
                            delay(50);
                        } catch (Exception e) {
                            logE(TAG, "doRotationTest()" + e.toString());
                            testFinish = true;
                        }
                    }
                    logE(TAG, "doRotationTest()退出循环" );
                }
            });
            IMoveAction turnAction = slamWarePlatform.rotate(rotation);
            turnAction.waitUntilDone();
            testFinish = true;
        } catch (Exception e) {
            testFinish = true;
        }
    }*/

}
