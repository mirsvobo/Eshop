package org.example.eshop.dto;

import lombok.Data;
import org.example.eshop.model.Image; // Import pro snadnější mapování

/**
 * DTO for returning essential image information to the frontend after upload.
 */
@Data // Lombok pro gettery, settery, toString atd.
public class ImageDto {
    private Long id;
    private String url;
    private String altText;
    private Integer displayOrder;

    // Konstruktor pro snadné mapování z entity (volitelné, ale užitečné)
    public ImageDto(Image image) {
        if (image != null) {
            this.id = image.getId();
            this.url = image.getUrl();
            this.altText = image.getAltText();
            this.displayOrder = image.getDisplayOrder();
        }
    }

    // Výchozí konstruktor (Lombok @Data ho negeneruje, pokud existuje jiný)
    public ImageDto() {}
}
