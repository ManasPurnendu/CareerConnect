import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { recruiterApi } from '../../api/recruiter';
import type { Job } from '../../types/api';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { Button } from '../../components/ui/Button';
import { Pagination } from '../../components/ui/Pagination';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';
import { Skeleton } from '../../components/ui/Skeleton';
import { EmptyState } from '../../components/ui/EmptyState';

export const RecruiterJobsPage: React.FC = () => {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState<Job[]>([]);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const fetchJobs = async () => {
      setIsLoading(true);
      try {
        const response = await recruiterApi.getJobs(page, 10);
        if (response?.content) {
          setJobs(response.content);
          setTotalPages(response.page?.totalPages ?? 1);
        }
      } finally {
        setIsLoading(false);
      }
    };
    fetchJobs();
  }, [page]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-headline font-bold text-2xl text-slate-text">Job Postings</h1>
          <p className="text-xs text-slate-muted mt-0.5">Manage and track your company openings</p>
        </div>
        <Link to="/recruiter/jobs/new">
          <Button variant="primary" size="md" leftIcon={<span className="material-symbols-outlined !text-base">add</span>}>
            Post New Role
          </Button>
        </Link>
      </div>

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Company Openings</h2>
        </CardHeader>
        <CardBody className="p-0">
          {isLoading ? (
            <div className="p-4 space-y-3">
              <Skeleton height="40px" />
              <Skeleton height="40px" />
            </div>
          ) : jobs.length === 0 ? (
            <EmptyState
              icon="work_outline"
              title="No Job Postings"
              description="Your company has not published any openings yet."
              actionLabel="Create First Posting"
              onAction={() => navigate('/recruiter/jobs/new')}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-[#FAFAF8] text-slate-muted text-[11px] uppercase tracking-wider border-b border-outline-warm">
                  <tr>
                    <th className="px-4 py-3">Job Title</th>
                    <th className="px-4 py-3">Type</th>
                    <th className="px-4 py-3">Location</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-warm/70">
                  {jobs.map((job) => (
                    <tr key={job.id} className="hover:bg-[#F9FAF9]">
                      <td className="px-4 py-3.5 font-medium text-slate-text">{job.title}</td>
                      <td className="px-4 py-3.5 text-xs text-slate-muted">{job.jobType}</td>
                      <td className="px-4 py-3.5 text-xs text-slate-muted">{job.location}</td>
                      <td className="px-4 py-3.5">
                        <StatusBadge status={job.status} size="sm" />
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => navigate(`/recruiter/applications?jobId=${job.id}`)}
                        >
                          View Applicants
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardBody>
      </Card>

      {totalPages > 1 && (
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          onPageChange={(newPage) => setPage(newPage)}
        />
      )}
    </div>
  );
};
