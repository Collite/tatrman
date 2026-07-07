select account_id, branch_code, region
    from erp.accounts
    where status = 'ACTIVE'
