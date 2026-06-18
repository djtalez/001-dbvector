package com.edu.vectorlab.controller;

import com.edu.vectorlab.model.DocumentPayload;
import com.edu.vectorlab.model.PdfIndexResult;
import com.edu.vectorlab.model.SearchResultDto;
import com.edu.vectorlab.service.VectorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vector")
@CrossOrigin(origins = "*")
public class VectorController {

    private final VectorService vectorService;

    public VectorController(VectorService vectorService) {
        this.vectorService = vectorService;
    }

    @PostMapping("/index")
    public ResponseEntity<Map<String, String>> indexDocument(@RequestBody DocumentPayload payload) {
        String category = payload.category() != null ? payload.category() : "Non classé";
        vectorService.indexDocument(payload.content(), Map.of("category", category, "sourceType", "text"));
        return ResponseEntity.ok(Map.of("status", "Document indexé avec succès !"));
    }

    @PostMapping("/index-pdf")
    public ResponseEntity<PdfIndexResult> indexPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category) {
        try {
            return ResponseEntity.ok(vectorService.indexPdf(file, category));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire le PDF.");
        }
    }

    @GetMapping("/embed-debug")
    public ResponseEntity<Map<String, Object>> debugEmbedding(@RequestParam String text) {
        float[] vector = vectorService.getEmbeddingAsArray(text);
        float[] preview = Arrays.copyOfRange(vector, 0, Math.min(10, vector.length));
        return ResponseEntity.ok(Map.of(
                "length", vector.length,
                "preview", preview,
                "fullVector", vector
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResultDto>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0.3") double threshold,
            @RequestParam(defaultValue = "3") int topK) {
        return ResponseEntity.ok(vectorService.searchSimilarDocuments(query, threshold, topK));
    }
}
