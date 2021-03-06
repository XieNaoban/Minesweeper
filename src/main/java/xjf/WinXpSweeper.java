package xjf;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 从屏幕上找取 Windows XP 的原版扫雷程序窗口, 通过操纵原版扫雷来实现扫雷玩法.
 */
public class WinXpSweeper extends MineSweeper {
    public static final int GAME_RULE_REAL_WIN_XP = 20200728;

    // 左上角的格子在截图的坐标
    private static final int OFFSET_X = 3;
    private static final int OFFSET_Y = 46;

    private Robot robot;                // 用于操作鼠标
    private Rectangle boardPosition;    // 记录扫雷窗口的位置
    private Point lastMouseLocation;    // 记录鼠标位置

    /**
     * 默认构造函数, 从屏幕找到 winmine.exe 窗口并读取棋局
     */
    public WinXpSweeper() { this.initXpGame(false); }

    /**
     * 构造函数
     * @param newRound true: 自动点击黄脸重开一局新的; false: 读取当前局面
     */
    public WinXpSweeper(boolean newRound) { this.initXpGame(newRound); }

    /**
     * 初始化. 从屏幕截图中找到 WinXP 扫雷程序, 从中读取所有信息
     * @param newRound 是否先开启一局新的 (还是从当前局面开始)
     */
    private void initXpGame(boolean newRound) {
        try { this.robot = new Robot(); } catch (AWTException e) {
            throw new RuntimeException("Robot 初始化失败.");
        }
        BufferedImage image = this.captureScreen();
        Point yellowFace = this.findYellowFace(image);
        if (yellowFace == null) throw new WindowOccludedException();
        Rectangle rect = this.boardPosition = this.findWindow(image, yellowFace);
        this.storeMousePosition();
        this.activateWindow();
        if (newRound) {
            this.robot.mouseMove(yellowFace.x, yellowFace.y);
            this.robot.mousePress(InputEvent.BUTTON1_MASK);
            this.robot.mouseRelease(InputEvent.BUTTON1_MASK);
            this.robot.delay(14);
            image = this.captureBoard();
        }
        else image = image.getSubimage(rect.x, rect.y, rect.width, rect.height);
        this.restoreMousePosition();

        int row = (image.getHeight() - OFFSET_Y - OFFSET_X) / 16;
        int col = (image.getWidth() - 2 * OFFSET_X) / 16;
        int mine = this.getMine(image);
        int [][] board = new int[row][col];
        for (int i = 0; i < row; ++i) for (int j = 0; j < col; ++j) {
            board[i][j] = this.getCell(image, i, j);
            if (board[i][j] == FLAG) ++mine;
        }
        this.initGame(row, col, mine, false, null, GAME_RULE_REAL_WIN_XP);
        this.playerBoard = board;
        this.state = this.getYellowFaceState(image);
    }

    /**
     * 快速挖掘, 提高 AI 性能用 (将一些不着急计算或加载的内容挪到了 lazyUpdate() 里)
     * 比直接 dig()、mark() 快了四倍以上.
     * @param x x
     * @param y x
     */
    @Override
    public void quickDig(int x, int y) {
        this.mouseMoveAndClick(x, y, InputEvent.BUTTON1_MASK);
        if (this.playerBoard[x][y] == UNCHECKED || this.playerBoard[x][y] == QUESTION) ++step;
    }

    /**
     * 快速标旗, 提高 AI 性能用 (将一些不着急计算或加载的内容挪到了 lazyUpdate() 里)
     * 比直接 dig()、mark() 快了四倍以上.
     * @param x x
     * @param y x
     */
    @Override
    public void quickFlag(int x, int y) {
        int times = 0;
        if (this.playerBoard[x][y] == UNCHECKED) times = 1;
        else if (this.playerBoard[x][y] == QUESTION) times = 2;
        while (times-- > 0) {
            this.mouseMoveAndClick(x, y, InputEvent.BUTTON3_MASK);
        }
        if (this.playerBoard[x][y] != FLAG) {
            this.playerBoard[x][y] = FLAG;
            --this.mineLeft;
            ++step;
        }
    }

    /**
     * 配合 quickDig()、quickFlag() 使用, 更新棋盘信息
     * quickDig()、quickFlag() 不会更新棋盘状态, 所以返回 void.
     */
    @Override
    public void lazyUpdate() { this.updateGameState(); }

    /**
     * 带有自检的安全的挖掘操作 (所以会比较慢)
     * @param x 目标格子的 x 坐标
     * @param y 目标格子的 y 坐标
     * @return 游戏状态
     */
    @Override
    public int dig(int x, int y) {
        this.pointRangeCheck(x, y);
        this.storeMousePosition();
        this.captureBoard();
        this.activateWindow();
        this.mouseMoveAndClick(x, y, InputEvent.BUTTON1_MASK);
        this.restoreMousePosition();
        return this.updateGameState();
    }

    /**
     * 带有自检的安全的标记操作 (所以会比较慢)
     * @param x 目标格子的 x 坐标
     * @param y 目标格子的 y 坐标
     * @return 游戏状态
     */
    @Override
    public int mark(int x, int y) {
        this.pointRangeCheck(x, y);
        this.storeMousePosition();
        this.captureBoard();
        this.activateWindow();
        this.mouseMoveAndClick(x, y, InputEvent.BUTTON3_MASK);
        this.restoreMousePosition();
        return this.updateGameState();
    }

    /**
     * 带有自检的安全的检查操作 (所以会比较慢)
     * @param x 目标格子的 x 坐标
     * @param y 目标格子的 y 坐标
     * @return 游戏状态
     */
    @Override
    public int check(int x, int y) {
        this.pointRangeCheck(x, y);
        this.storeMousePosition();
        this.captureBoard();
        this.activateWindow();
        this.mouseMoveAndClick(x, y, InputEvent.BUTTON2_MASK);
        this.restoreMousePosition();
        return this.updateGameState();
    }

    /**
     * 返回当前未知格子数量
     * 父类的 coveredCellLeft 未在本类中使用, 所以要通过遍历棋盘的方式计算该返回值
     * @return 当前未知格子数量
     */
    @Override
    public int getUncheckedCellLeft() {
        int res = 0;
        for (int i = 0; i < this.row; ++i) for (int j = 0; j < this.col; ++j) {
            if (this.playerBoard[i][j] == UNCHECKED || this.playerBoard[i][j] == QUESTION) ++res;
        }
        return res;
    }

    /**
     * 鼠标操作完后, 截图检查剩余雷数、黄脸状态、棋盘变化等
     * @return 新的游戏状态
     */
    private int updateGameState() {
        this.robot.delay(this.row * this.col / 160 + 6);
        BufferedImage image = this.captureBoard();
        ++this.step;
        for (int i = 0; i < row; ++i) for (int j = 0; j < col; ++j) {
            this.playerBoard[i][j] = this.getCell(image, i, j);
        }
        this.mineLeft = this.getMine(image);
        return this.state = this.getYellowFaceState(image);
    }

    /**
     * 截取扫雷窗口 (而不是全屏)
     * @return 截图
     */
    private BufferedImage captureBoard() {
        BufferedImage image = this.captureScreen(this.boardPosition);
        if (image.getRGB(0, 0) != -8355712 || image.getRGB(image.getWidth() - 1, 1) != -1
                || image.getRGB(image.getWidth() - 2, 0) != -8355712
                || image.getRGB(image.getWidth() - 1, image.getHeight() - 1) != -1
                || image.getRGB(1, image.getHeight() - 1) != -1
                || image.getRGB(0, image.getHeight() - 2) != -8355712) {
            image = this.captureScreen();
            Point yellowFace = this.findYellowFace(image);
            if (yellowFace == null) throw new WindowOccludedException();
            Rectangle rect = this.boardPosition = this.findWindow(image, yellowFace);
            image = image.getSubimage(rect.x, rect.y, rect.width, rect.height);
        }
        return image;
    }

    /**
     * 全屏截图
     * @return 截图
     */
    private BufferedImage captureScreen() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return this.captureScreen(new Rectangle(screenSize));
    }

    /**
     * 给定区域截图
     * @param size 截图区域
     * @return 截图
     */
    private BufferedImage captureScreen(Rectangle size) {
        return this.robot.createScreenCapture(size);
    }

    /**
     * 从全屏截图中找到黄脸 (我是通过找黄脸来定位窗口位置的)
     * @param image 全屏截图
     * @return 黄脸坐标
     */
    private Point findYellowFace(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        for (int i = 0; i < width; ++i) for (int j = 0; j < height; ++j) {
            if (image.getRGB(i, j) == -256
                    && image.getRGB(i - 8, j) == -16777216 && image.getRGB(i, j - 8) == -16777216
                    && image.getRGB(i + 8, j) == -16777216 && image.getRGB(i, j + 8) == -16777216
                    && image.getRGB(i - 7, j + 1) == -256 && image.getRGB(i, j - 7) == -256
                    && image.getRGB(i + 7, j + 1) == -256 && image.getRGB(i, j + 7) == -256) {
                return new Point(i, j);
            }
        }
        return null;
    }

    /**
     * 从全屏截图找到窗口 (仅截取有用的部分窗口画布)
     * @param image 全屏截图
     * @param yellowFace 黄脸位置
     * @return 窗口位置
     */
    private Rectangle findWindow(BufferedImage image, Point yellowFace) {
        int x = yellowFace.x, y = yellowFace.y + 24;
        int x1 = x, y1 = yellowFace.y - 19;
        int x2 = x, y2 = y;
        while (image.getRGB(x1, y) == -8355712) --x1;
        ++x1;
        while (image.getRGB(x2, y) == -8355712) ++x2;
        ++y2;
        while (image.getRGB(x2, y2) == -1) ++y2;
        --y2;
        if (x1 + x2 + 1 >> 1 != x || image.getRGB(x1 + 1, y2) != -1 || image.getRGB(x1, y2 - 1) != -8355712) {
            throw new WindowOccludedException(image.getSubimage(x1, y1, x2 - x1 + 1, y2 - y1 + 1));
        }
        return new Rectangle(x1, y1, x2 - x1 + 1, y2 - y1 + 1);
    }

    /**
     * 获取扫雷窗口里八位数码管显示的数字
     * @param image 截图
     * @param left 数码管坐标 (左)
     * @param top 数码管坐标 (上)
     * @return 数码管数字
     */
    private int getNumberOfLed(BufferedImage image, int left, int top) {
        boolean vTop = image.getRGB(left + 5, top + 1) == -65536;
        boolean vMid = image.getRGB(left + 5, top + 10) == -65536;
        boolean vBot = image.getRGB(left + 5, top + 19) == -65536;
        boolean vL1 = image.getRGB(left + 1, top + 5) == -65536;
        boolean vL2 = image.getRGB(left + 1, top + 15) == -65536;
        boolean vR1 = image.getRGB(left + 9, top + 5) == -65536;
        boolean vR2 = image.getRGB(left + 9, top + 15) == -65536;
        if (!vR2) return 2;
        if (vL2) {
            if (!vR1) return 6;
            return vMid ? 8 : 0;
        }
        if (!vR1) return 5;
        if (vBot) return vL1 ? 9: 3;
        if (vMid) return 4;
        if (vTop) return 7;
        return 1;
    }

    /**
     * 获取一个格子的值
     * @param image 截图
     * @param x 格子所在行
     * @param y 格子所在列
     * @return 格子的值
     */
    private int getCell(BufferedImage image, int x, int y) {
        int cx = y * 16 + OFFSET_X, cy = x * 16 + OFFSET_Y;
        switch (image.getRGB(cx + 7, cy + 8)) {
            case -4144960:
                if (image.getRGB(cx, cy) == -1) {
                    return image.getRGB(cx + 7, cy + 7) == -65536 ? FLAG : UNCHECKED;
                }
                return image.getRGB(cx + 3, cy + 3) == -16777216 ? 7 : 0;
            case -16776961: return 1;
            case -16744448: return 2;
            case -65536: return image.getRGB(cx + 6, cy + 8) == -16777216 ? NOT_MINE : 3;
            case -16777088: return 4;
            case -8388608: return 5;
            case -16744320: return 6;
            case -8355712: return 8;
            case -16777216:
                if (image.getRGB(cx + 7, cy + 7) == -4144960) return QUESTION;
                return image.getRGB(cx + 1, cy + 1) == -65536 ? RED_MINE : MINE;
            default: throw new RuntimeException("识别不出来");
        }
    }

    /**
     * 根据窗口截图获得黄脸状态
     * @param image 窗口截图
     * @return 游戏状态
     */
    private int getYellowFaceState(BufferedImage image) {
        if (image.getRGB(image.getWidth() / 2, 21) == -16777216) return LOSE;
        return image.getRGB(image.getWidth() / 2, 16) == -16777216 ? WIN : PROCESS;
    }

    /**
     * 根据窗口截图获取雷数
     * @param image 窗口截图
     * @return 雷数
     */
    private int getMine(BufferedImage image) {
        return getNumberOfLed(image, 9, 8) * 100 + getNumberOfLed(image, 22, 8) * 10
                + getNumberOfLed(image, 35, 8);
    }

    /**
     * 根据格子的行列, 计算格子在全屏幕截图中的坐标
     * @param x 格子所在行
     * @param y 格子所在列
     * @return 屏幕坐标
     */
    private Point getScreenPosition(int x, int y) {
        return new Point(y * 16 + 7 + OFFSET_X + boardPosition.x,
                x * 16 + 7 + OFFSET_Y + boardPosition.y);
    }

    /**
     * 移动鼠标并点击
     * @param x 格子所在行
     * @param y 格子所在列
     * @param action 左键、右键等
     */
    private void mouseMoveAndClick(int x, int y, int action) {
        Point p = this.getScreenPosition(x, y);
        this.robot.mouseMove(p.x, p.y);
        this.robot.mousePress(action);
        this.robot.mouseRelease(action);
    }

    /**
     * 激活窗口 (winmine.exe 在未激活时点击是无效的)
     * 原理就是在窗口随便一个空白位置点一下.
     */
    private void activateWindow() {
        this.robot.mouseMove(this.boardPosition.x + this.boardPosition.width / 2, this.boardPosition.y);
        this.robot.mousePress(InputEvent.BUTTON1_MASK);
        this.robot.mouseRelease(InputEvent.BUTTON1_MASK);
    }

    /**
     * 保存鼠标位置
     */
    private void storeMousePosition() {
        this.lastMouseLocation = MouseInfo.getPointerInfo().getLocation();
    }

    /**
     * 鼠标挪到之前的位置
     */
    private void restoreMousePosition() {
        this.robot.mouseMove(this.lastMouseLocation.x, this.lastMouseLocation.y);
    }

    /**
     * 保存截图到当前目录
     * @param image 截图
     * @param filename 文件名 (不包含后缀. 后缀已被指定为 .png)
     */
    public static void saveImage(BufferedImage image, String filename) {
        try {
            ImageIO.write(image, "png", new File(filename + ".png"));
        } catch (IOException e) {
            System.err.println("截图生成失败.");
            e.printStackTrace();
        }
    }

    /**
     * 窗口被遮挡而无法全部识别时抛出的异常
     */
    public static class WindowOccludedException extends RuntimeException {
        public WindowOccludedException() {
            super("winmine.exe 窗口可能被遮挡、移动或关闭!");
        }

        public WindowOccludedException(BufferedImage image) {
            super("winmine.exe 窗口可能被遮挡、移动或关闭! 问题截图 cap.png 已保存在当前目录.");
            saveImage(image, "cap");
        }
    }
}
