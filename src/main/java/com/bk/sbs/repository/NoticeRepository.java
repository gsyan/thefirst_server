package com.bk.sbs.repository;

import com.bk.sbs.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findByEnabledTrueOrderByDisplayOrderAsc();

    List<Notice> findAllByOrderByDisplayOrderAsc();
}
