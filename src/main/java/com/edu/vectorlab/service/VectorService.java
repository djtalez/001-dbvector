package com.edu.vectorlab.service;

import com.edu.vectorlab.model.PdfIndexResult;
import com.edu.vectorlab.model.SearchResultDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VectorService {

    private final QdrantVectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final PdfExtractionService pdfExtractionService;
    private final int chunkSize;
    private final int chunkOverlap;

    public VectorService(
            QdrantVectorStore vectorStore,
            EmbeddingModel embeddingModel,
            PdfExtractionService pdfExtractionService,
            @Value("${vector-lab.pdf.chunk-size:800}") int chunkSize,
            @Value("${vector-lab.pdf.chunk-overlap:100}") int chunkOverlap) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.pdfExtractionService = pdfExtractionService;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public void indexDocument(String content, Map<String, Object> metadata) {
        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
    }

    public PdfIndexResult indexPdf(MultipartFile file, String category) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier PDF est vide.");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        if (!filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Seuls les fichiers PDF sont acceptés.");
        }

        String extractedText = pdfExtractionService.extractText(file.getInputStream());
        if (extractedText.isBlank()) {
            throw new IllegalArgumentException("Aucun texte extractible dans ce PDF.");
        }

        List<String> chunks = splitIntoChunks(extractedText);
        String resolvedCategory = category != null && !category.isBlank() ? category : "Non classé";

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("category", resolvedCategory);
            metadata.put("sourceType", "pdf");
            metadata.put("filename", filename);
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", chunks.size());
            documents.add(new Document(chunks.get(i), metadata));
        }

        vectorStore.add(documents);
        return new PdfIndexResult(filename, chunks.size(), extractedText.length());
    }

    public float[] getEmbeddingAsArray(String text) {
        return embeddingModel.embed(text);
    }

    public List<SearchResultDto> searchSimilarDocuments(String query, double threshold, int topK) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);

        return results.stream()
                .map(doc -> new SearchResultDto(doc.getText(), extractSimilarityScore(doc), doc.getMetadata()))
                .collect(Collectors.toList());
    }

    List<String> splitIntoChunks(String text) {
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - chunkOverlap, start + 1);
        }
        return chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
    }

    private double extractSimilarityScore(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();

        if (metadata.containsKey("score")) {
            return normalizeScore(toDouble(metadata.get("score")));
        }
        if (metadata.containsKey("distance")) {
            return normalizeScore(1.0 - toDouble(metadata.get("distance")));
        }
        return 0.0;
    }

    private double normalizeScore(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
