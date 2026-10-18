import React from 'react';
import { Card, CardHeader, CardBody } from '../../components/ui/Card';

export const CandidateNotificationsPage: React.FC = () => {
  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="font-headline font-bold text-2xl text-slate-text">Notifications</h1>
        <p className="text-xs text-slate-muted mt-0.5">Real-time alerts regarding your applications</p>
      </div>

      <Card>
        <CardHeader>
          <h2 className="font-headline font-semibold text-base text-slate-text">Recent Alerts</h2>
        </CardHeader>
        <CardBody className="p-8 text-center text-xs text-slate-muted">
          <span className="material-symbols-outlined !text-3xl text-slate-muted mb-2 block">
            notifications_none
          </span>
          You have no unread notifications at this time.
        </CardBody>
      </Card>
    </div>
  );
};
