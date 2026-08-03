-- 한국어는 공백 기준 기본 파서로는 검색이 잘 안 돼서 ngram 파서로 생성.
ALTER TABLE `chat_messages`
  ADD FULLTEXT INDEX `idx_chat_messages_content_fulltext` (`content`) WITH PARSER ngram;
