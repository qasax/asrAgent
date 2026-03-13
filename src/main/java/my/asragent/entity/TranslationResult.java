package my.asragent.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.sql.Timestamp;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *  实体类。
 *
 * @author zhangfajin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("translation_result")
public class TranslationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long userId;

    private String translationText;

    private String summaryText;

    private String todoText;

    private String sseConnectionId;

    private Timestamp createdAt;

    private Timestamp updatedAt;

    private String fullText;

    private String imgUrl;

    private String extend1;

    private String extend2;

    private String extend3;

    private String extend4;

}
