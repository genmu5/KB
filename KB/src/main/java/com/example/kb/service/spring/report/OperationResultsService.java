package com.example.kb.service.spring.report;

import com.example.kb.dto.spring.GptRequest;
import com.example.kb.dto.spring.GptResponse;
import com.example.kb.entity.spring.report.OperationResults;
import com.example.kb.repository.primary.report.OperationResultsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OperationResultsService {

    @Value("${chatGptApiUrl}")
    private String chatGptApiUrl;

    @Value("${chatGptApiKey}")
    private String chatGptApiKey;

    private final RestTemplate restTemplate;
    private final OperationResultsRepository operationResultsRepository;
    private final JdbcTemplate jdbcTemplate;


    @Autowired
    public OperationResultsService(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate, OperationResultsRepository operationResultsRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = new RestTemplate();
        this.operationResultsRepository = operationResultsRepository;
    }

    public String generateAndSaveOperationResults(String fundName, String operationPeriod, List<String> newsSummaries) throws IOException {
        String previousResults = getPreviousOperationResultsFromDB(fundName, operationPeriod);
        String resultsPrompt = createOperationResultsPrompt(fundName, operationPeriod, newsSummaries);
        String commentary = requestGptResponse(resultsPrompt);
        saveOperationResults(fundName, operationPeriod, commentary);
        return commentary;
    }

    private String getPreviousOperationResultsFromDB(String fundName, String operationPeriod) {
        // 데이터베이스에서 쿼리 실행하여 결과 가져오기
        String sql = "SELECT Commentary FROM operation_results WHERE Fund_Name = ? AND Operation_Period != ?;";
        return jdbcTemplate.queryForObject(sql, new Object[]{fundName, operationPeriod}, String.class);
    }

    private String requestGptResponse(String prompt) throws IOException {
        GptRequest gptRequest = new GptRequest("gpt-4o", prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + chatGptApiKey);
        headers.set("Content-Type", "application/json");

        HttpEntity<GptRequest> requestEntity = new HttpEntity<>(gptRequest, headers);
        ResponseEntity<GptResponse> responseEntity = restTemplate.exchange(
                chatGptApiUrl + "/chat/completions",
                HttpMethod.POST,
                requestEntity,
                GptResponse.class
        );

        if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
            return responseEntity.getBody().getChoices().get(0).getMessage().getContent().trim();
        } else {
            throw new IOException("Unexpected response status: " + responseEntity.getStatusCode());
        }
    }

    private String createOperationResultsPrompt(String fundName, String operationPeriod, List<String> newsSummaries) {
        String articlesText = newsSummaries.stream().collect(Collectors.joining("\n"));
        String coment = getPreviousOperationResultsFromDB(fundName, operationPeriod);
        return String.format(
                "펀드 이름: %s\n운용 기간: %s\n관련 뉴스 요약:\n%s\n위 이전버전의 시장 현황 및 운용경과는 %s\n 이 정보를 바탕으로 펀드의 시장 현황 및 운용경과를 작성해 주세요.",
                fundName, operationPeriod, articlesText,coment
        );
//        String prompt = String.format(
//                "펀드 이름: %s\n운용 기간: %s\n관련 뉴스 요약:\n%s\n이전 운용 결과: %s\n이 정보를 바탕으로 펀드의 운용 결과를 작성해 주세요.",
//                fundName, operationPeriod, articlesText, coment
//        );
//
//        // 생성된 프롬프트를 콘솔에 출력
//        System.out.println("생성된 프롬프트:\n" + prompt);
//
//        return prompt;
    }

    private void saveOperationResults(String fundName, String operationPeriod, String commentary) {
        OperationResults results = new OperationResults();
        results.setFundName(fundName);
        results.setOperationPeriod(operationPeriod);
        results.setCommentary(commentary);
        operationResultsRepository.save(results);
    }


}
