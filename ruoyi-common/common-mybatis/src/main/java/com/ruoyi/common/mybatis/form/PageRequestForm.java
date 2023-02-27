//package com.ruoyi.common.mybatis.form;
//
//
//public class PageRequestForm implements PageForm {
//    public boolean equals(Object o) {
//        if (o == this)
//            return true;
//        if (!(o instanceof PageRequestForm))
//            return false;
//        PageRequestForm other = (PageRequestForm)o;
//        if (!other.canEqual(this))
//            return false;
//        if (getStart() != other.getStart())
//            return false;
//        if (getLimit() != other.getLimit())
//            return false;
//        if (getPage() != other.getPage())
//            return false;
//        Object this$sort = getSort(), other$sort = other.getSort();
//        if ((this$sort == null) ? (other$sort != null) : !this$sort.equals(other$sort))
//            return false;
//        Object this$direction = getDirection(), other$direction = other.getDirection();
//        return !((this$direction == null) ? (other$direction != null) : !this$direction.equals(other$direction));
//    }
//
//    protected boolean canEqual(Object other) {
//        return other instanceof PageRequestForm;
//    }
//
//    public int hashCode() {
//        int PRIME = 59;
//        int result = 1;
//        result = result * 59 + getStart();
//        result = result * 59 + getLimit();
//        result = result * 59 + getPage();
//        Object $sort = getSort();
//        result = result * 59 + (($sort == null) ? 43 : $sort.hashCode());
//        Object $direction = getDirection();
//        return result * 59 + (($direction == null) ? 43 : $direction.hashCode());
//    }
//
//    private int start = 0;
//
//    private int limit = 10;
//
//    private int page = 1;
//
//    private String sort;
//
//    private String direction = "DESC";
//
//    public PageRequestForm(int start, int limit) {
//        this.start = start;
//        this.limit = limit;
//    }
//
//    public PageRequestForm(int page) {
//        this.page = page;
//    }
//
//    public int getStart() {
//        return this.start;
//    }
//
//    public void setStart(int start) {
//        this.start = start;
//        this.page = this.start / this.limit + 1;
//    }
//
//    public int getLimit() {
//        return this.limit;
//    }
//
//    public void setLimit(int limit) {
//        this.limit = limit;
//    }
//
//    public String getSort() {
//        return this.sort;
//    }
//
//    public void setSort(String sort) {
//        this.sort = sort;
//    }
//
//    public String getDirection() {
//        return this.direction;
//    }
//
//    public void setDirection(String direction) {
//        this.direction = direction;
//    }
//
//    public int getPage() {
//        return this.page;
//    }
//
//    public void setPage(int page) {
//        this.page = page;
//        this.start = (this.page - 1) * this.limit;
//    }
//
//    public PageRequestForm() {}
//}
