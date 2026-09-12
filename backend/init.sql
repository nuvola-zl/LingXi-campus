CREATE TABLE "public"."kb_media" (
                                     "id" int8 NOT NULL DEFAULT nextval('kb_media_id_seq'::regclass),
                                     "library_id" int8 NOT NULL,
                                     "file_name" varchar(512) COLLATE "pg_catalog"."default" NOT NULL,
                                     "mime_type" varchar(128) COLLATE "pg_catalog"."default" NOT NULL,
                                     "file_size" int8 NOT NULL,
                                     "storage_path" varchar(2048) COLLATE "pg_catalog"."default" NOT NULL,
                                     "sha256" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
                                     "status" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'PENDING'::character varying,
                                     "total_chunks" int4 DEFAULT 0,
                                     "parsed_chunks" int4 DEFAULT 0,
                                     "error_message" varchar(512) COLLATE "pg_catalog"."default",
                                     "created_at" timestamp(0) NOT NULL DEFAULT now(),
                                     "updated_at" timestamp(0) NOT NULL DEFAULT now(),
                                     CONSTRAINT "kb_media_pkey" PRIMARY KEY ("id"),
                                     CONSTRAINT "uk_media_sha256_library" UNIQUE ("sha256", "library_id")
)
;

ALTER TABLE "public"."kb_media"
    OWNER TO "postgres";

CREATE INDEX "idx_media_created_at" ON "public"."kb_media" USING btree (
    "created_at" "pg_catalog"."timestamp_ops" ASC NULLS LAST
    );

CREATE INDEX "idx_media_library_id" ON "public"."kb_media" USING btree (
    "library_id" "pg_catalog"."int8_ops" ASC NULLS LAST
    );

CREATE INDEX "idx_media_sha256" ON "public"."kb_media" USING btree (
    "sha256" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

CREATE INDEX "idx_media_status" ON "public"."kb_media" USING btree (
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

CREATE TRIGGER "update_kb_media_updated_at" BEFORE UPDATE ON "public"."kb_media"
    FOR EACH ROW
    EXECUTE PROCEDURE "public"."update_updated_at_column"();

COMMENT ON COLUMN "public"."kb_media"."status" IS '解析状态: PENDING/PARSING/PARSED/FAILED';

COMMENT ON TABLE "public"."kb_media" IS '媒体表-存储知识库内上传的文件元信息';

CREATE TABLE "public"."kb_library" (
                                       "id" int8 NOT NULL DEFAULT nextval('kb_library_id_seq'::regclass),
                                       "name" varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
                                       "description" varchar(255) COLLATE "pg_catalog"."default",
                                       "type" varchar(16) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'personal'::character varying,
                                       "owner_id" int8 NOT NULL,
                                       "is_top" bool NOT NULL DEFAULT false,
                                       "cover_image" varchar(600) COLLATE "pg_catalog"."default",
                                       "created_at" timestamp(0) NOT NULL DEFAULT now(),
                                       "updated_at" timestamp(0) NOT NULL DEFAULT now(),
                                       CONSTRAINT "kb_library_pkey" PRIMARY KEY ("id"),
                                       CONSTRAINT "uk_library_name_owner" UNIQUE ("name", "owner_id")
)
;

ALTER TABLE "public"."kb_library"
    OWNER TO "postgres";

CREATE INDEX "idx_library_created_at" ON "public"."kb_library" USING btree (
    "created_at" "pg_catalog"."timestamp_ops" ASC NULLS LAST
    );

CREATE INDEX "idx_library_is_top" ON "public"."kb_library" USING btree (
    "is_top" "pg_catalog"."bool_ops" ASC NULLS LAST
    );

CREATE INDEX "idx_library_owner_id" ON "public"."kb_library" USING btree (
    "owner_id" "pg_catalog"."int8_ops" ASC NULLS LAST
    );

CREATE INDEX "idx_library_type" ON "public"."kb_library" USING btree (
    "type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

CREATE TRIGGER "update_kb_library_updated_at" BEFORE UPDATE ON "public"."kb_library"
    FOR EACH ROW
    EXECUTE PROCEDURE "public"."update_updated_at_column"();

COMMENT ON COLUMN "public"."kb_library"."type" IS '知识库类型: personal/team';

COMMENT ON COLUMN "public"."kb_library"."owner_id" IS '所属用户ID';

COMMENT ON TABLE "public"."kb_library" IS '知识库表';

CREATE TABLE "public"."kb_chunk" (
                                     "id" int8 NOT NULL DEFAULT nextval('kb_chunk_id_seq'::regclass),
                                     "library_id" int8 NOT NULL,
                                     "media_id" int8 NOT NULL,
                                     "content" text COLLATE "pg_catalog"."default" NOT NULL,
                                     "embedding" "public"."vector",
                                     "chunk_index" int4 NOT NULL,
                                     "metadata" jsonb,
                                     "created_at" timestamp(0) NOT NULL DEFAULT now(),
                                     CONSTRAINT "kb_chunk_pkey" PRIMARY KEY ("id")
)
;

ALTER TABLE "public"."kb_chunk"
    OWNER TO "postgres";

CREATE INDEX "idx_chunk_content_fulltext" ON "public"."kb_chunk" USING gin (
    to_tsvector('simple'::regconfig, content) "pg_catalog"."tsvector_ops"
    );

CREATE INDEX "idx_chunk_embedding" ON "public"."kb_chunk" (
                                                           "embedding" "public"."vector_cosine_ops" ASC NULLS LAST
    );

CREATE INDEX "idx_chunk_library_id" ON "public"."kb_chunk" USING btree (
    "library_id" "pg_catalog"."int8_ops" ASC NULLS LAST
    );

CREATE INDEX "idx_chunk_media_id" ON "public"."kb_chunk" USING btree (
    "media_id" "pg_catalog"."int8_ops" ASC NULLS LAST
    );

COMMENT ON COLUMN "public"."kb_chunk"."embedding" IS '向量嵌入(1024维)';

COMMENT ON TABLE "public"."kb_chunk" IS '分片表-存储解析后的文本分片及向量嵌入';




