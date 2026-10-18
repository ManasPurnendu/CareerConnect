import React from 'react';
import { CompanyCard } from '../../components/ui/CompanyCard';
import type { Company } from '../../types/api';

export const CompaniesPage: React.FC = () => {
  const sampleCompanies: Company[] = [
    {
      id: 1,
      name: 'Nexus Innovations',
      description: 'Building next-generation developer tooling and cloud platforms with a strong mentorship culture for early-career engineers.',
      location: 'San Francisco, CA / Remote',
      industry: 'Cloud Infrastructure',
      size: '150-500',
      isVerified: true,
    },
    {
      id: 2,
      name: 'Apex Analytics',
      description: 'Modern enterprise analytics platform specializing in scalable distributed pipelines and accessible data visualizations.',
      location: 'Bengaluru, India / Hybrid',
      industry: 'Data & Analytics',
      size: '50-150',
      isVerified: true,
    },
    {
      id: 3,
      name: 'CloudScale Systems',
      description: 'High-performance backend systems and telemetry infrastructure for real-time mission-critical applications.',
      location: 'Austin, TX / On-site',
      industry: 'Software Engineering',
      size: '500-1000',
      isVerified: true,
    },
  ];

  return (
    <div className="layout-container py-8 sm:py-10 space-y-6">
      <div>
        <h1 className="font-headline font-bold text-2xl sm:text-3xl text-slate-text">
          Featured & Verified Companies
        </h1>
        <p className="text-xs sm:text-sm text-slate-muted mt-1">
          Explore vetted employers actively hiring and supporting early-career talent.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-4">
        {sampleCompanies.map((comp) => (
          <CompanyCard key={comp.id} company={comp} />
        ))}
      </div>
    </div>
  );
};
