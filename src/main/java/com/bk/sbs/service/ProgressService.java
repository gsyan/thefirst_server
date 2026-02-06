//--------------------------------------------------------------------------------------------------
package com.bk.sbs.service;

import com.bk.sbs.dto.ProgressInfoDto;
import com.bk.sbs.dto.ProgressListResponse;
import com.bk.sbs.dto.ProgressSaveRequest;
import com.bk.sbs.entity.Progress;
import com.bk.sbs.repository.ProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProgressService {

    private final ProgressRepository progressRepository;

    public ProgressService(ProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    @Transactional
    public ProgressInfoDto saveProgress(Long characterId, ProgressSaveRequest request) {
        // 이미 존재하는 경우 무시 (중복 저장 방지)
        if (progressRepository.existsByCharacterIdAndCategoryAndProgressKey(
                characterId, request.getCategory(), request.getKey())) {
            Progress existing = progressRepository.findByCharacterIdAndCategoryAndProgressKey(
                    characterId, request.getCategory(), request.getKey()).get();
            return toDto(existing);
        }

        Progress progress = new Progress();
        progress.setCharacterId(characterId);
        progress.setCategory(request.getCategory());
        progress.setProgressKey(request.getKey());
        progress.setCompletedDateTime(Instant.now());

        Progress saved = progressRepository.save(progress);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ProgressListResponse getProgressList(Long characterId, String category) {
        List<Progress> progressList = progressRepository.findByCharacterIdAndCategory(characterId, category);

        List<ProgressInfoDto> dtoList = progressList.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ProgressListResponse.builder()
                .progressList(dtoList)
                .build();
    }

    private ProgressInfoDto toDto(Progress progress) {
        return ProgressInfoDto.builder()
                .category(progress.getCategory())
                .key(progress.getProgressKey())
                .completedDateTime(progress.getCompletedDateTime().toString())
                .build();
    }
}
