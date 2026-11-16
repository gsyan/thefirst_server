package com.bk.sbs.repository;

import com.bk.sbs.entity.CommanderNoticeSeen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommanderNoticeSeenRepository extends JpaRepository<CommanderNoticeSeen, Long> {

    // 이 커맨더가 지금까지 본 공지 전체를 한 번에 조회(활성 공지 목록과 메모리상 대조, N+1 방지)
    List<CommanderNoticeSeen> findByCommanderId(Long commanderId);

    Optional<CommanderNoticeSeen> findByCommanderIdAndNoticeId(Long commanderId, Long noticeId);

    // 공지 삭제 시 그 공지의 열람 기록도 함께 정리
    @Modifying
    @Query("DELETE FROM CommanderNoticeSeen s WHERE s.noticeId = :noticeId")
    void deleteAllByNoticeId(@Param("noticeId") Long noticeId);
}
