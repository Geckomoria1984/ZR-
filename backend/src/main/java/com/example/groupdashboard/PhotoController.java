package com.example.groupdashboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PhotoController {
  private static final List<String> EXTENSIONS = List.of(".png", "_5.png", ".jpg", ".jpeg");
  private final DashboardProperties properties;

  public PhotoController(DashboardProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/api/photos/{personId}")
  public ResponseEntity<Resource> photo(@PathVariable String personId) throws IOException {
    for (String extension : EXTENSIONS) {
      Path photo = properties.photoDir().resolve(personId + extension);
      if (Files.isRegularFile(photo)) {
        return ResponseEntity.ok()
            .contentType(contentType(photo))
            .body(new FileSystemResource(photo));
      }
    }
    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
  }

  private MediaType contentType(Path photo) throws IOException {
    String probe = Files.probeContentType(photo);
    return probe == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(probe);
  }
}
