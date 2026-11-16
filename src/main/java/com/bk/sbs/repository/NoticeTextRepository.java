package com.bk.sbs.repository;

import com.bk.sbs.entity.NoticeText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NoticeTextRepository extends JpaRepository<NoticeText, Long> {

    List<NoticeText> findByNoticeIdIn(Collection<Long> noticeIds);

    List<NoticeText> findByNoticeId(Long noticeId);

    // 같은 트랜잭션에서 곧바로 같은 (noticeId, locale)을 다시 insert하므로 즉시 실행되는 벌크 삭제를 씀(파생 deleteBy는 insert보다 늦게 flush됨)
    @Modifying
    @Query("DELETE FROM NoticeText t WHERE t.noticeId = :noticeId")
    void deleteAllByNoticeId(@Param("noticeId") Long noticeId);
}
