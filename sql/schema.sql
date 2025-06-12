--
-- PostgreSQL database dump
--

-- Dumped from database version 16.8 (Debian 16.8-1.pgdg120+1)
-- Dumped by pg_dump version 16.8 (Debian 16.8-1.pgdg120+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: api_employee_info; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.api_employee_info (
    id bigint NOT NULL,
    arcidno character varying(255),
    company_code character varying(255),
    company_name character varying(255),
    company_partyid bigint,
    country_code character varying(255),
    created_date timestamp without time zone,
    data_created_date timestamp without time zone,
    data_created_user character varying(255),
    data_modified_date timestamp without time zone,
    data_modified_user character varying(255),
    date_of_birth timestamp without time zone,
    email_address character varying(255),
    employed_status character varying(255),
    employee_no character varying(255),
    employee_type_code character varying(255),
    employee_type_name character varying(255),
    english_name character varying(255),
    ext_no character varying(255),
    form_org_code character varying(255),
    form_org_name character varying(255),
    form_org_partyid bigint,
    formula_org_code character varying(255),
    formula_org_name character varying(255),
    formula_org_partyid bigint,
    full_name character varying(255),
    function_org_code character varying(255),
    function_org_name character varying(255),
    gender_code character varying(255),
    gender_name character varying(255),
    hire_date timestamp without time zone,
    idno character varying(255),
    idno_suffix character varying(255),
    job_flag character varying(255),
    job_grade_code character varying(255),
    job_grade_name character varying(255),
    job_level_code character varying(255),
    job_level_name character varying(255),
    job_title_code character varying(255),
    job_title_name character varying(255),
    mobile_phone_no character varying(255),
    mvpn character varying(255),
    office_phone character varying(255),
    party_roleid bigint,
    passport_no character varying(255),
    permanent_address character varying(255),
    permanent_phone_no character varying(255),
    permanent_zip_code character varying(255),
    position_code character varying(255),
    position_name character varying(255),
    present_address character varying(255),
    present_phone_no character varying(255),
    present_zip_code character varying(255),
    resignation_date timestamp without time zone,
    status character varying(255),
    tenantid character varying(255),
    userid character varying(255)
);


ALTER TABLE public.api_employee_info OWNER TO postgres;

--
-- Name: api_employee_info_action_log; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.api_employee_info_action_log (
    id bigint NOT NULL,
    action character varying(255),
    action_date timestamp without time zone,
    created_date timestamp without time zone NOT NULL,
    employee_no character varying(255) NOT NULL,
    field_name character varying(255) NOT NULL,
    new_value character varying(255),
    old_value character varying(255),
    party_roleid bigint
);


ALTER TABLE public.api_employee_info_action_log OWNER TO postgres;

--
-- Name: api_employee_info_action_log_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.api_employee_info_action_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.api_employee_info_action_log_id_seq OWNER TO postgres;

--
-- Name: api_employee_info_action_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.api_employee_info_action_log_id_seq OWNED BY public.api_employee_info_action_log.id;


--
-- Name: api_employee_info_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.api_employee_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.api_employee_info_id_seq OWNER TO postgres;

--
-- Name: api_employee_info_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.api_employee_info_id_seq OWNED BY public.api_employee_info.id;


--
-- Name: api_organization; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.api_organization (
    id bigint NOT NULL,
    address character varying(50),
    company_code character varying(20),
    company_name character varying(50),
    company_partyid bigint,
    created_date timestamp without time zone,
    data_created_date timestamp without time zone,
    data_created_user character varying(30),
    data_modified_date timestamp without time zone,
    data_modified_user character varying(30),
    email character varying(50),
    end_date timestamp without time zone,
    english_name character varying(50),
    fax character varying(10),
    layer_code character varying(5),
    layer_name character varying(20),
    org_code character varying(20),
    org_name character varying(50),
    org_property_code character varying(2),
    organizationid bigint,
    remark character varying(20),
    sort_sequence integer,
    start_date timestamp without time zone,
    status character varying(2),
    telephone character varying(20),
    tenantid character varying(10)
);


ALTER TABLE public.api_organization OWNER TO postgres;

--
-- Name: api_organization_action_log; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.api_organization_action_log (
    id bigint NOT NULL,
    action character varying(10) NOT NULL,
    action_date timestamp without time zone NOT NULL,
    created_date timestamp without time zone NOT NULL,
    field_name character varying(30) NOT NULL,
    is_sync boolean,
    new_value character varying(50),
    old_value character varying(50),
    org_code character varying(20) NOT NULL,
    organization_id bigint
);


ALTER TABLE public.api_organization_action_log OWNER TO postgres;

--
-- Name: api_organization_action_log_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.api_organization_action_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.api_organization_action_log_id_seq OWNER TO postgres;

--
-- Name: api_organization_action_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.api_organization_action_log_id_seq OWNED BY public.api_organization_action_log.id;


--
-- Name: api_organization_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.api_organization_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.api_organization_id_seq OWNER TO postgres;

--
-- Name: api_organization_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.api_organization_id_seq OWNED BY public.api_organization.id;


--
-- Name: api_organization_relation_action_log; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.api_organization_relation_action_log (
    id bigint NOT NULL,
    action character varying(10) NOT NULL,
    action_date timestamp without time zone NOT NULL,
    created_date timestamp without time zone NOT NULL,
    field_name character varying(30) NOT NULL,
    is_sync boolean,
    new_value character varying(50),
    old_value character varying(50),
    org_code character varying(20) NOT NULL,
    organization_relation_id bigint
);


ALTER TABLE public.api_organization_relation_action_log OWNER TO postgres;

--
-- Name: api_organization_relation_action_log_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.api_organization_relation_action_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.api_organization_relation_action_log_id_seq OWNER TO postgres;

--
-- Name: api_organization_relation_action_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.api_organization_relation_action_log_id_seq OWNED BY public.api_organization_relation_action_log.id;


--
-- Name: api_employee_info id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_employee_info ALTER COLUMN id SET DEFAULT nextval('public.api_employee_info_id_seq'::regclass);


--
-- Name: api_employee_info_action_log id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_employee_info_action_log ALTER COLUMN id SET DEFAULT nextval('public.api_employee_info_action_log_id_seq'::regclass);


--
-- Name: api_organization id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_organization ALTER COLUMN id SET DEFAULT nextval('public.api_organization_id_seq'::regclass);


--
-- Name: api_organization_action_log id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_organization_action_log ALTER COLUMN id SET DEFAULT nextval('public.api_organization_action_log_id_seq'::regclass);


--
-- Name: api_organization_relation_action_log id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_organization_relation_action_log ALTER COLUMN id SET DEFAULT nextval('public.api_organization_relation_action_log_id_seq':
:regclass);


--
-- Name: api_employee_info_action_log api_employee_info_action_log_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_employee_info_action_log
    ADD CONSTRAINT api_employee_info_action_log_pkey PRIMARY KEY (id);


--
-- Name: api_employee_info api_employee_info_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_employee_info
    ADD CONSTRAINT api_employee_info_pkey PRIMARY KEY (id);


--
-- Name: api_organization_action_log api_organization_action_log_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_organization_action_log
    ADD CONSTRAINT api_organization_action_log_pkey PRIMARY KEY (id);


--
-- Name: api_organization api_organization_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_organization
    ADD CONSTRAINT api_organization_pkey PRIMARY KEY (id);


--
-- Name: api_organization_relation_action_log api_organization_relation_action_log_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_organization_relation_action_log
    ADD CONSTRAINT api_organization_relation_action_log_pkey PRIMARY KEY (id);


--
-- PostgreSQL database dump complete
--