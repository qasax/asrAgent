package my.asragent.model;

/**
 * 发送给 SSE 客户端的统一载荷封装。
 *
 * 不同的 `type` 值决定填充哪些字段。
 */
public class ServerMessage {
    /** 事件类型：partial/final/translation/summary/qa/error/... */
    private String type;
    /** 用于 partial/final/translation/summary 事件的通用文本字段。 */
    private String text;
    /** 人类可读的错误信息。 */
    private String message;
    /** 机器可读的错误码。 */
    private String code;
    /** `qa` 事件的问题字段。 */
    private String question;
    /** `qa` 事件的答案字段。 */
    private String answer;

    /** 创建非最终转写消息。 */
    public static ServerMessage partial(String text) {
        ServerMessage msg = new ServerMessage();
        msg.type = "partial";
        msg.text = text;
        return msg;
    }

    /** 创建最终转写消息。 */
    public static ServerMessage finalText(String text) {
        ServerMessage msg = new ServerMessage();
        msg.type = "final";
        msg.text = text;
        return msg;
    }

    /** 创建翻译消息。 */
    public static ServerMessage translation(String text) {
        ServerMessage msg = new ServerMessage();
        msg.type = "translation";
        msg.text = text;
        return msg;
    }
    /** 创建快速翻译消息。 */
    public static ServerMessage quickTranslation(String text) {
        ServerMessage msg = new ServerMessage();
        msg.type = "quickTranslation";
        msg.text = text;
        return msg;
    }

    /** 创建摘要消息。 */
    public static ServerMessage summary(String text) {
        ServerMessage msg = new ServerMessage();
        msg.type = "summary";
        msg.text = text;
        return msg;
    }

    /** 创建问答消息。 */
    public static ServerMessage qa(String question, String answer) {
        ServerMessage msg = new ServerMessage();
        msg.type = "qa";
        msg.question = question;
        msg.answer = answer;
        return msg;
    }

    /** 创建错误消息。 */
    public static ServerMessage error(String code, String message) {
        ServerMessage msg = new ServerMessage();
        msg.type = "error";
        msg.code = code;
        msg.message = message;
        return msg;
    }

    /** 创建通用类型文本消息。 */
    public static ServerMessage info(String type, String text) {
        ServerMessage msg = new ServerMessage();
        msg.type = type;
        msg.text = text;
        return msg;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }
}
